// Client de ChatAPI : REST pour le compte, la liste des utilisateurs et l'historique,
// WebSocket pour recevoir les messages et la présence en temps réel.
"use strict";

// sessionStorage est propre à chaque onglet : deux utilisateurs peuvent tester côte à côte sur un même PC.
const etat = {
    token: sessionStorage.getItem("token"),
    moi: JSON.parse(sessionStorage.getItem("moi") || "null"),
    utilisateurs: new Map(),
    interlocuteurId: null,
    socket: null
};

const el = id => document.getElementById(id);

const MESSAGES_HTTP = {
    400: "Nom d'au moins 3 caractères et mot de passe d'au moins 6 caractères",
    401: "Identifiants invalides",
    409: "Nom d'utilisateur déjà pris"
};

async function api(methode, url, corps) {
    const entetes = {"Content-Type": "application/json"};
    if (etat.token) {
        entetes.Authorization = "Bearer " + etat.token;
    }
    const reponse = await fetch(url, {
        method: methode,
        headers: entetes,
        body: corps ? JSON.stringify(corps) : undefined
    });
    if (reponse.status === 401 && etat.token) {
        // Jeton expiré, ou serveur redémarré sans JWT_SECRET : il faut se reconnecter.
        deconnecter("Session expirée, reconnectez-vous");
        throw new Error("Session expirée");
    }
    if (!reponse.ok) {
        throw new Error(MESSAGES_HTTP[reponse.status] || "Erreur du serveur (" + reponse.status + ")");
    }
    return reponse.json();
}

function afficherStatut(texte, erreur = false) {
    const statut = el("statut");
    statut.textContent = texte;
    statut.classList.toggle("erreur", erreur);
}

async function seConnecter(creerCompte) {
    const identifiants = {username: el("username").value.trim(), password: el("password").value};
    try {
        if (creerCompte) {
            await api("POST", "/api/comptes", identifiants);
        }
        const reponse = await api("POST", "/api/auth/login", identifiants);
        etat.token = reponse.token;
        etat.moi = {id: reponse.id, username: reponse.username};
        sessionStorage.setItem("token", etat.token);
        sessionStorage.setItem("moi", JSON.stringify(etat.moi));
        el("password").value = "";
        demarrer();
    } catch (e) {
        afficherStatut(e.message, true);
    }
}

function demarrer() {
    el("vue-connexion").hidden = true;
    el("vue-chat").hidden = false;
    el("moi").textContent = etat.moi.username;
    afficherStatut("");
    // Le canal est ouvert avant de charger la liste : aucun changement de présence n'est manqué entre les deux.
    ouvrirSocket();
    chargerUtilisateurs().catch(e => afficherStatut(e.message, true));
}

function deconnecter(raison) {
    const socket = etat.socket;
    etat.socket = null;
    if (socket) {
        socket.close();
    }
    sessionStorage.clear();
    Object.assign(etat, {token: null, moi: null, interlocuteurId: null, utilisateurs: new Map()});
    document.body.classList.remove("conversation-ouverte");
    el("vue-chat").hidden = true;
    el("vue-connexion").hidden = false;
    afficherStatut(raison || "", Boolean(raison));
}

function ouvrirSocket() {
    const protocole = location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(protocole + "//" + location.host + "/ws/messages?token="
        + encodeURIComponent(etat.token));
    socket.onmessage = evenement => traiterTrame(JSON.parse(evenement.data));
    socket.onclose = () => {
        // Une fermeture voulue (déconnexion) a déjà remis etat.socket à null.
        if (etat.socket === socket) {
            afficherStatut("Connexion temps réel perdue, rechargez la page", true);
        }
    };
    etat.socket = socket;
}

function traiterTrame(trame) {
    switch (trame.type) {
        case "message":
            recevoirMessage(trame);
            break;
        case "presence":
            changerPresence(trame);
            break;
        case "erreur":
            afficherStatut(trame.detail, true);
            break;
    }
}

function recevoirMessage(message) {
    if (message.expediteurId === etat.interlocuteurId) {
        ajouterBulle(message);
        return;
    }
    const expediteur = etat.utilisateurs.get(message.expediteurId);
    if (expediteur) {
        expediteur.nonLus++;
        afficherUtilisateurs();
    }
}

function changerPresence(presence) {
    const utilisateur = etat.utilisateurs.get(presence.utilisateurId);
    if (!utilisateur) {
        // Compte créé après le chargement de la liste : on la recharge.
        chargerUtilisateurs().catch(e => afficherStatut(e.message, true));
        return;
    }
    utilisateur.enLigne = presence.enLigne;
    afficherUtilisateurs();
}

async function chargerUtilisateurs() {
    const liste = await api("GET", "/api/utilisateurs");
    const precedents = etat.utilisateurs;
    etat.utilisateurs = new Map(liste
        .filter(u => u.id !== etat.moi.id)
        .map(u => [u.id, {...u, nonLus: precedents.get(u.id)?.nonLus || 0}]));
    afficherUtilisateurs();
}

function afficherUtilisateurs() {
    const liste = el("liste-utilisateurs");
    liste.replaceChildren();
    for (const utilisateur of etat.utilisateurs.values()) {
        const bouton = document.createElement("button");
        bouton.type = "button";
        bouton.classList.toggle("actif", utilisateur.id === etat.interlocuteurId);
        bouton.onclick = () => ouvrirConversation(utilisateur.id);

        const pastille = document.createElement("span");
        pastille.className = "pastille" + (utilisateur.enLigne ? " en-ligne" : "");
        pastille.title = utilisateur.enLigne ? "En ligne" : "Hors ligne";

        // textContent et non innerHTML : un nom ou un message ne peut jamais injecter de HTML (XSS).
        const nom = document.createElement("span");
        nom.textContent = utilisateur.username;

        bouton.append(pastille, nom);
        if (utilisateur.nonLus > 0) {
            const badge = document.createElement("span");
            badge.className = "non-lus";
            badge.textContent = utilisateur.nonLus;
            bouton.append(badge);
        }
        const element = document.createElement("li");
        element.append(bouton);
        liste.append(element);
    }
}

async function ouvrirConversation(id) {
    const interlocuteur = etat.utilisateurs.get(id);
    etat.interlocuteurId = id;
    interlocuteur.nonLus = 0;
    afficherUtilisateurs();
    document.body.classList.add("conversation-ouverte");
    el("titre-conversation").textContent = interlocuteur.username;
    el("messages").replaceChildren();
    for (const element of el("form-message").elements) {
        element.disabled = false;
    }
    try {
        // L'historique vient de REST : il contient aussi les messages reçus pendant que l'on était hors ligne.
        const historique = await api("GET", "/api/messages/" + id);
        if (etat.interlocuteurId === id) {
            historique.forEach(ajouterBulle);
        }
    } catch (e) {
        afficherStatut(e.message, true);
    }
    el("contenu").focus();
}

function fermerConversation() {
    etat.interlocuteurId = null;
    document.body.classList.remove("conversation-ouverte");
    afficherUtilisateurs();
}

function ajouterBulle(message) {
    const bulle = document.createElement("div");
    bulle.className = "bulle " + (message.expediteurId === etat.moi.id ? "moi" : "autre");
    const contenu = document.createElement("p");
    contenu.textContent = message.contenu;
    // Un message que l'on vient d'envoyer n'a pas encore de date serveur : l'heure locale est affichée.
    const heure = document.createElement("small");
    heure.textContent = message.dateEnvoi
        ? message.dateEnvoi.slice(11, 16)
        : new Date().toTimeString().slice(0, 5);
    bulle.append(contenu, heure);
    const zone = el("messages");
    zone.append(bulle);
    zone.scrollTop = zone.scrollHeight;
}

function envoyerMessage(evenement) {
    evenement.preventDefault();
    const champ = el("contenu");
    const contenu = champ.value.trim();
    if (!contenu || etat.interlocuteurId === null) {
        return;
    }
    if (!etat.socket || etat.socket.readyState !== WebSocket.OPEN) {
        afficherStatut("Connexion temps réel indisponible, rechargez la page", true);
        return;
    }
    // Ni expéditeur ni date : le serveur les fixe à partir du jeton et de son horloge.
    etat.socket.send(JSON.stringify({type: "message", destinataireId: etat.interlocuteurId, contenu}));
    ajouterBulle({expediteurId: etat.moi.id, contenu});
    champ.value = "";
}

el("form-connexion").addEventListener("submit", evenement => {
    evenement.preventDefault();
    seConnecter(false);
});
el("bouton-creer").addEventListener("click", () => {
    if (el("form-connexion").reportValidity()) {
        seConnecter(true);
    }
});
el("bouton-deconnexion").addEventListener("click", () => deconnecter());
el("bouton-retour").addEventListener("click", fermerConversation);
el("form-message").addEventListener("submit", envoyerMessage);

if (etat.token && etat.moi) {
    demarrer();
}
