// Client de ChatAPI : REST pour le compte, la liste des utilisateurs et l'historique,
// WebSocket pour recevoir les messages, les accusés et la présence en temps réel.
"use strict";

// sessionStorage est propre à chaque onglet : deux utilisateurs peuvent tester côte à côte sur un même PC.
const etat = {
    token: sessionStorage.getItem("token"),
    moi: JSON.parse(sessionStorage.getItem("moi") || "null"),
    utilisateurs: new Map(),
    interlocuteurId: null,
    socket: null,
    // Mes bulles de la conversation ouverte, par id de message, pour y reporter les accusés.
    bulles: new Map(),
    // Destinataire prévenu que j'écris (null sinon), et minuteurs de l'indicateur de saisie.
    saisieVers: null,
    minuteurSaisie: null,
    minuteurIndicateur: null,
    minuteurStatut: null,
    // Jour du dernier message affiché ("2026-09-21"), pour insérer un séparateur quand il change.
    dernierJour: null
};

// Sans frappe pendant ce délai, la saisie est considérée comme terminée.
const DELAI_FIN_SAISIE_MS = 2000;
// Filet de sécurité : l'indicateur s'efface seul si la fin de saisie n'arrive jamais (appareil déconnecté).
const DUREE_MAX_INDICATEUR_MS = 5000;
const DUREE_STATUT_MS = 5000;

const el = id => document.getElementById(id);

// Ordre du cycle de vie : un accusé n'est appliqué que s'il fait avancer le statut affiché.
const RANG_STATUT = {ENVOYE: 0, LIVRE: 1, LU: 2};
const LIBELLE_STATUT = {ENVOYE: "Envoyé", LIVRE: "Livré", LU: "Lu"};

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
    clearTimeout(etat.minuteurStatut);
    if (texte) {
        etat.minuteurStatut = setTimeout(() => afficherStatut(""), DUREE_STATUT_MS);
    }
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
    arreterSaisie();
    afficherIndicateurSaisie(false);
    const socket = etat.socket;
    etat.socket = null;
    if (socket) {
        socket.close();
    }
    sessionStorage.clear();
    Object.assign(etat, {token: null, moi: null, interlocuteurId: null, utilisateurs: new Map(), bulles: new Map()});
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
        case "accuse":
            recevoirAccuse(trame);
            break;
        case "saisie":
            recevoirSaisie(trame);
            break;
        case "presence":
            changerPresence(trame);
            break;
        case "erreur":
            afficherStatut(trame.detail, true);
            break;
    }
}

// Reçoit aussi l'écho de mes propres messages, envoyés depuis cet appareil ou un autre.
function recevoirMessage(message) {
    const deMoi = message.expediteurId === etat.moi.id;
    const interlocuteurId = deMoi ? message.destinataireId : message.expediteurId;
    if (interlocuteurId === etat.interlocuteurId) {
        ajouterBulle(message);
        if (!deMoi) {
            afficherIndicateurSaisie(false);
            envoyerLu(interlocuteurId);
        }
        return;
    }
    const interlocuteur = etat.utilisateurs.get(interlocuteurId);
    if (interlocuteur && !deMoi) {
        interlocuteur.nonLus++;
        afficherUtilisateurs();
    }
}

function recevoirAccuse(accuse) {
    const bulle = etat.bulles.get(accuse.messageId);
    if (bulle && RANG_STATUT[accuse.statut] > RANG_STATUT[bulle.statut]) {
        bulle.statut = accuse.statut;
        bulle.element.textContent = LIBELLE_STATUT[accuse.statut];
    }
}

// Renvoie false si le canal n'est pas ouvert : la trame n'est alors pas envoyée.
function envoyerTrame(trame) {
    if (!etat.socket || etat.socket.readyState !== WebSocket.OPEN) {
        return false;
    }
    etat.socket.send(JSON.stringify(trame));
    return true;
}

// Signale au serveur que la conversation est affichée : ses messages passent en LU.
function envoyerLu(interlocuteurId) {
    envoyerTrame({type: "lu", interlocuteurId});
}

// Appelée à chaque frappe : une seule trame au début de la saisie, une autre après un temps d'inactivité.
function signalerSaisie() {
    if (!el("contenu").value.trim()) {
        arreterSaisie();
        return;
    }
    if (etat.saisieVers === null
        && envoyerTrame({type: "saisie", destinataireId: etat.interlocuteurId, enCours: true})) {
        etat.saisieVers = etat.interlocuteurId;
    }
    clearTimeout(etat.minuteurSaisie);
    etat.minuteurSaisie = setTimeout(arreterSaisie, DELAI_FIN_SAISIE_MS);
}

function arreterSaisie() {
    clearTimeout(etat.minuteurSaisie);
    if (etat.saisieVers !== null) {
        envoyerTrame({type: "saisie", destinataireId: etat.saisieVers, enCours: false});
        etat.saisieVers = null;
    }
}

function recevoirSaisie(saisie) {
    if (saisie.expediteurId === etat.interlocuteurId) {
        afficherIndicateurSaisie(saisie.enCours);
    }
}

function afficherIndicateurSaisie(visible) {
    clearTimeout(etat.minuteurIndicateur);
    el("saisie").hidden = !visible;
    if (visible) {
        etat.minuteurIndicateur = setTimeout(() => afficherIndicateurSaisie(false), DUREE_MAX_INDICATEUR_MS);
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
    if (presence.utilisateurId === etat.interlocuteurId) {
        afficherEtatConversation();
        if (!presence.enLigne) {
            afficherIndicateurSaisie(false);
        }
    }
    afficherUtilisateurs();
}

function afficherEtatConversation() {
    el("etat-conversation").textContent = etat.utilisateurs.get(etat.interlocuteurId).enLigne
        ? "En ligne"
        : "Hors ligne";
}

async function chargerUtilisateurs() {
    const liste = await api("GET", "/api/utilisateurs");
    const precedents = etat.utilisateurs;
    etat.utilisateurs = new Map(liste
        .filter(u => u.id !== etat.moi.id)
        .map(u => [u.id, {...u, nonLus: precedents.get(u.id)?.nonLus || 0}]));
    afficherUtilisateurs();
}

// Crée un élément avec une classe et un texte ; textContent et non innerHTML :
// un nom ou un message ne peut jamais injecter de HTML (XSS).
function creer(balise, classe, texte = "") {
    const element = document.createElement(balise);
    element.className = classe;
    element.textContent = texte;
    return element;
}

function afficherUtilisateurs() {
    const liste = el("liste-utilisateurs");
    liste.replaceChildren();
    if (etat.utilisateurs.size === 0) {
        liste.append(creer("li", "liste-vide", "Aucun autre utilisateur pour l'instant."));
        return;
    }
    for (const utilisateur of etat.utilisateurs.values()) {
        const bouton = creer("button", "contact");
        bouton.type = "button";
        bouton.classList.toggle("actif", utilisateur.id === etat.interlocuteurId);
        bouton.onclick = () => ouvrirConversation(utilisateur.id);

        const avatar = creer("span", "avatar", utilisateur.username.charAt(0));
        avatar.append(creer("span", "pastille" + (utilisateur.enLigne ? " en-ligne" : "")));

        const identite = creer("span", "identite");
        identite.append(creer("span", "nom", utilisateur.username),
            creer("span", "etat", utilisateur.enLigne ? "En ligne" : "Hors ligne"));

        bouton.append(avatar, identite);
        if (utilisateur.nonLus > 0) {
            bouton.append(creer("span", "non-lus", utilisateur.nonLus));
        }
        const element = document.createElement("li");
        element.append(bouton);
        liste.append(element);
    }
}

async function ouvrirConversation(id) {
    const interlocuteur = etat.utilisateurs.get(id);
    arreterSaisie();
    afficherIndicateurSaisie(false);
    etat.interlocuteurId = id;
    interlocuteur.nonLus = 0;
    afficherUtilisateurs();
    document.body.classList.add("conversation-ouverte");
    el("titre-conversation").textContent = interlocuteur.username;
    afficherEtatConversation();
    el("messages").replaceChildren();
    etat.bulles = new Map();
    etat.dernierJour = null;
    for (const element of el("form-message").elements) {
        element.disabled = false;
    }
    try {
        // L'historique vient de REST : il contient aussi les messages reçus pendant que l'on était hors ligne.
        const historique = await api("GET", "/api/messages/" + id);
        if (etat.interlocuteurId === id) {
            historique.forEach(ajouterBulle);
            envoyerLu(id);
        }
    } catch (e) {
        afficherStatut(e.message, true);
    }
    // Sur téléphone, donner le focus ouvrirait le clavier et masquerait l'historique.
    if (matchMedia("(pointer: fine)").matches) {
        el("contenu").focus();
    }
}

function fermerConversation() {
    arreterSaisie();
    afficherIndicateurSaisie(false);
    etat.interlocuteurId = null;
    document.body.classList.remove("conversation-ouverte");
    afficherUtilisateurs();
}

function ajouterBulle(message) {
    // Un message peut arriver par le WebSocket pendant le chargement de l'historique : pas de doublon.
    if (etat.bulles.has(message.id)) {
        return;
    }
    const zone = el("messages");
    // Date fixée par le serveur, au format "2026-09-21T10:15:30" : jour, puis heure et minutes.
    const jour = message.dateEnvoi.slice(0, 10);
    if (jour !== etat.dernierJour) {
        etat.dernierJour = jour;
        zone.append(creer("p", "jour", libelleJour(jour)));
    }
    const deMoi = message.expediteurId === etat.moi.id;
    const bulle = creer("div", "bulle " + (deMoi ? "moi" : "autre"));
    const pied = creer("small", "", message.dateEnvoi.slice(11, 16));
    const statut = creer("span", "statut", deMoi ? LIBELLE_STATUT[message.statut] : "");
    if (deMoi) {
        pied.append(" - ", statut);
    }
    etat.bulles.set(message.id, {element: statut, statut: message.statut});
    bulle.append(creer("p", "", message.contenu), pied);
    zone.append(bulle);
    zone.scrollTop = zone.scrollHeight;
}

function libelleJour(jour) {
    const date = new Date(jour + "T00:00:00");
    if (date.toDateString() === new Date().toDateString()) {
        return "Aujourd'hui";
    }
    return date.toLocaleDateString("fr-FR", {weekday: "long", day: "numeric", month: "long"});
}

function envoyerMessage(evenement) {
    evenement.preventDefault();
    const champ = el("contenu");
    const contenu = champ.value.trim();
    if (!contenu || etat.interlocuteurId === null) {
        return;
    }
    arreterSaisie();
    // Ni expéditeur ni date : le serveur les fixe à partir du jeton et de son horloge.
    // La bulle sera affichée à la réception de l'écho, avec l'id et la date du serveur.
    if (!envoyerTrame({type: "message", destinataireId: etat.interlocuteurId, contenu})) {
        afficherStatut("Connexion temps réel indisponible, rechargez la page", true);
        return;
    }
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
el("contenu").addEventListener("input", signalerSaisie);
document.addEventListener("keydown", evenement => {
    if (evenement.key === "Escape" && etat.interlocuteurId !== null) {
        fermerConversation();
    }
});

if (etat.token && etat.moi) {
    demarrer();
}
