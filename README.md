# ChatAPI

Messagerie instantanée entre deux appareils distincts, réalisée pour le projet de synthèse du module **API & Web Services** (cycle ingénieur, EFREI).

Deux utilisateurs, l'un sur un téléphone et l'autre sur un ordinateur, échangent des messages en temps réel. Les appareils ne communiquent jamais directement : chaque message passe par le serveur ChatAPI, qui l'enregistre puis le retransmet au destinataire.

## Fonctionnalités

| Fonctionnalité | Technologie | État |
|---|---|---|
| Création de compte et connexion | REST + JWT | Fait |
| Liste des utilisateurs avec leur statut en ligne | REST | Fait |
| Historique d'une conversation | REST | Fait |
| Protection des endpoints (401 sans jeton valide) | Spring Security + JWT | Fait |
| Envoi et réception en direct, message enregistré avant livraison | WebSocket | Fait |
| Présence en ligne et hors ligne en direct | WebSocket | Fait |
| Client web de test, utilisable sur téléphone et sur ordinateur | HTML / CSS / JavaScript | Fait |
| Interfaces finales mobile et bureau | HTML / CSS / JavaScript | Prévu |
| Bonus : accusés de réception et de lecture (envoyé, livré, lu) | WebSocket | Fait |
| Bonus : indicateur de saisie | WebSocket | Fait |
| Bonus : notification hors ligne par webhook | HTTP sortant | Fait |
| Bonus : limitation de débit des messages | WebSocket | Fait |
| Bonus : documentation OpenAPI et Swagger UI | REST | Fait |

## Lancer le projet

**Prérequis : Java 17 ou plus récent.** Rien d'autre à installer : Gradle, le JDK 21 du projet et la base de données sont fournis ou téléchargés automatiquement.

```powershell
cd chatapi
.\gradlew.bat bootRun        # Windows
./gradlew bootRun            # macOS / Linux
```

Le serveur démarre sur **http://localhost:8080**. Le premier lancement est plus long, le temps de télécharger les dépendances.

Ouvrir **http://localhost:8080** dans le navigateur affiche le client web. Pour essayer seul sur un ordinateur, ouvrir deux onglets et se connecter avec un compte différent dans chacun (par exemple `seyni` et `fode`) : chaque onglet garde son propre jeton.

Par défaut :
- les données sont enregistrées dans une base **H2** (`chatapi/data/`), elles sont conservées entre deux redémarrages ;
- sans variable `JWT_SECRET`, une clé de signature aléatoire est générée au démarrage. Les jetons sont alors invalidés à chaque redémarrage : il suffit de se reconnecter.

### Lancer les tests

```powershell
cd chatapi
.\gradlew.bat test
```

Les tests utilisent une base H2 en mémoire et ne touchent pas aux données.

### Configuration facultative

| Variable | Rôle | Défaut |
|---|---|---|
| `JWT_SECRET` | Clé de signature des jetons (32 caractères minimum) | Clé aléatoire à chaque démarrage |
| `CHAT_WEBHOOK_URL` | URL appelée en `POST` quand le destinataire d'un message est hors ligne | Aucune : notification simulée dans les logs |
| `SPRING_PROFILES_ACTIVE=postgres` | Utiliser PostgreSQL au lieu de H2 | H2 |
| `DB_URL` | URL PostgreSQL (profil `postgres`) | `jdbc:postgresql://localhost:5432/chatapiDB` |
| `DB_USER` | Utilisateur PostgreSQL (profil `postgres`) | `postgres` |
| `DB_PASSWORD` | Mot de passe PostgreSQL (profil `postgres`) | aucun |

Exemple avec PostgreSQL (la base `chatapiDB` doit exister) :

```powershell
$env:SPRING_PROFILES_ACTIVE='postgres'
$env:DB_PASSWORD='mot_de_passe'
.\gradlew.bat bootRun
```

## API REST

Tous les échanges sont en JSON. Sauf la création de compte et la connexion, chaque requête doit porter l'en-tête `Authorization: Bearer <jeton>`, sinon le serveur répond `401 Unauthorized`.

| Méthode | URL | Rôle | Réponses |
|---|---|---|---|
| `POST` | `/api/comptes` | Créer un compte | `201`, `400` données invalides, `409` nom déjà pris |
| `POST` | `/api/auth/login` | Se connecter et recevoir un JWT | `200`, `401` identifiants invalides |
| `GET` | `/api/utilisateurs` | Lister les utilisateurs et leur statut `enLigne` | `200`, `401` |
| `GET` | `/api/messages/{utilisateurId}` | Historique avec un utilisateur | `200`, `401` |

**Créer un compte puis se connecter**

```http
POST /api/comptes
Content-Type: application/json

{ "username": "seyni", "password": "secret123" }
```

```http
POST /api/auth/login
Content-Type: application/json

{ "username": "seyni", "password": "secret123" }
```

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "type": "Bearer", "id": 1, "username": "seyni" }
```

**Message de l'historique**

```json
{ "id": 1, "expediteurId": 1, "destinataireId": 2, "contenu": "Bonjour !", "dateEnvoi": "2026-09-21T10:15:30", "statut": "LU" }
```

La documentation OpenAPI est consultable sur **http://localhost:8080/swagger-ui.html** : après la connexion, coller le jeton dans "Authorize" pour essayer les endpoints protégés. Une collection Postman documentée et testée est aussi fournie dans [`postman/`](postman/README.md).

## WebSocket

Le temps réel passe par une connexion WebSocket ouverte avec le jeton obtenu à la connexion :

```
ws://localhost:8080/ws/messages?token=<jeton>
```

Sans jeton valide, la connexion est refusée (`401`). Le jeton est passé dans l'URL car un navigateur ne peut pas ajouter d'en-tête `Authorization` à une connexion WebSocket.

| Type | Sens | Contenu |
|---|---|---|
| `message` | client vers serveur | `destinataireId`, `contenu` |
| `message` | serveur vers destinataire et expéditeur | `id`, `expediteurId`, `destinataireId`, `contenu`, `dateEnvoi`, `statut` |
| `accuse` | serveur vers expéditeur | `messageId`, `statut` (`LIVRE` ou `LU`) |
| `lu` | client vers serveur | `interlocuteurId` : la conversation est affichée |
| `saisie` | client vers serveur | `destinataireId`, `enCours` |
| `saisie` | serveur vers destinataire | `expediteurId`, `enCours` |
| `presence` | serveur vers les autres utilisateurs | `utilisateurId`, `enLigne` |
| `erreur` | serveur vers l'émetteur | `code` (`FORMAT_INVALIDE`, `TYPE_INCONNU`, `MESSAGE_INVALIDE`, `LIMITE_DEPASSEE`), `detail` |

**Envoyer un message**

```json
{ "type": "message", "destinataireId": 2, "contenu": "Bonjour !" }
```

**Message reçu par le destinataire**

```json
{ "type": "message", "id": 1, "expediteurId": 1, "destinataireId": 2, "contenu": "Bonjour !", "dateEnvoi": "2026-09-21T10:15:30", "statut": "ENVOYE" }
```

L'expéditeur est toujours celui du jeton et la date celle du serveur : un `expediteurId` ou une `dateEnvoi` envoyés par le client sont ignorés.

**Cycle d'un message**

1. Le message est enregistré en base avec le statut `ENVOYE`, avant toute tentative de livraison.
2. Il est renvoyé à l'expéditeur, qui obtient ainsi son `id` et la date du serveur.
3. Si le destinataire est connecté, il le reçoit immédiatement, le statut passe à `LIVRE` et l'expéditeur reçoit un `accuse`.
4. Sinon, le message attend en base et un webhook prévient un service tiers (simulé dans les logs si `CHAT_WEBHOOK_URL` n'est pas défini). À la connexion du destinataire, le statut passe à `LIVRE`.
5. Quand le destinataire ouvre la conversation, il envoie `lu` : les messages passent à `LU` et l'expéditeur reçoit un `accuse`.

Un statut n'avance jamais en arrière. Chaque utilisateur peut envoyer au plus 20 messages sur toute période de 10 secondes ; au-delà, il reçoit une `erreur` `LIMITE_DEPASSEE`.

## Tester depuis un téléphone

1. Le téléphone et le PC sont sur le même réseau Wi-Fi.
2. Récupérer l'adresse IPv4 du PC avec `ipconfig`.
3. Autoriser le port 8080 dans le pare-feu Windows (réseau privé).
4. Ouvrir `http://<IPv4 du PC>:8080` sur le téléphone.

Un VPN actif sur le PC peut bloquer l'accès depuis le réseau local : autoriser le réseau local dans ses réglages ou le désactiver le temps du test.

## Technologies

Java 21, Spring Boot 4.1 (Web MVC, WebSocket, Security, Data JPA, Validation), Gradle 9, JWT (jjwt), springdoc-openapi (Swagger UI), H2 et PostgreSQL, Lombok, HTML / CSS / JavaScript.

## Structure du projet

```
API-et-Web-Services/
├── README.md
├── sujet.pdf                                sujet du projet
├── postman/                                 collection Postman à importer
└── chatapi/                                 serveur Spring Boot
    ├── build.gradle, settings.gradle
    ├── gradlew, gradlew.bat                 Gradle fourni, rien à installer
    └── src/
        ├── main/java/fr/efrei/chatapi/
        │   ├── controller/                  endpoints REST
        │   │   ├── CompteController         POST /api/comptes
        │   │   ├── AuthController           POST /api/auth/login
        │   │   ├── UtilisateurController    GET  /api/utilisateurs
        │   │   └── MessageController        GET  /api/messages/{id}
        │   ├── websocket/                   temps réel
        │   │   ├── WebSocketConfig          point d'entrée /ws/messages
        │   │   ├── JwtHandshakeInterceptor  vérification du jeton à l'ouverture
        │   │   ├── SessionRegistry          connexions ouvertes par utilisateur
        │   │   └── MessageWebSocketHandler  réception, enregistrement et relais
        │   ├── security/                    SecurityConfig, JwtService, JwtAuthFilter
        │   ├── service/                     logique métier
        │   │   ├── UtilisateurService, MessageService
        │   │   ├── NotificationService      webhook hors ligne
        │   │   └── RateLimiter              limitation de débit
        │   ├── config/                      OpenApiConfig (documentation Swagger)
        │   ├── repository/                  accès à la base (Spring Data JPA)
        │   ├── model/                       entités Utilisateur et Message, StatutMessage
        │   └── dto/                         objets JSON échangés (records)
        ├── main/resources/
        │   ├── static/                          client web (index.html, chat.js, chat.css)
        │   ├── application.properties           configuration par défaut (H2)
        │   └── application-postgres.properties  profil PostgreSQL
        └── test/                            tests JUnit : API REST (MockMvc), WebSocket, limitation de débit
```

---

Projet réalisé en deuxième année du cycle ingénieur Big Data & Machine Learning, EFREI.
