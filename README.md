# ChatAPI

Messagerie instantanée entre deux appareils distincts, réalisée pour le projet de synthèse du module **API & Web Services** (cycle ingénieur, EFREI).

Deux utilisateurs, l'un sur un téléphone et l'autre sur un ordinateur, échangent des messages en temps réel. Les appareils ne communiquent jamais directement : chaque message passe par le serveur ChatAPI, qui l'enregistre puis le retransmet au destinataire.

## Fonctionnalités

| Fonctionnalité | Technologie | État |
|---|---|---|
| Création de compte et connexion | REST + JWT | Fait |
| Liste des utilisateurs | REST | Fait (statut en ligne à venir) |
| Historique d'une conversation | REST | Fait |
| Protection des endpoints (401 sans jeton valide) | Spring Security + JWT | Fait |
| Envoi et réception en direct | WebSocket | En cours |
| Clients mobile et bureau | HTML / CSS / JavaScript | En cours |
| Bonus : accusés de lecture, indicateur de saisie, webhook hors ligne, limitation de débit, Swagger | REST et WebSocket | Prévu |

## Lancer le projet

**Prérequis : Java 17 ou plus récent.** Rien d'autre à installer : Gradle, le JDK 21 du projet et la base de données sont fournis ou téléchargés automatiquement.

```powershell
cd chatapi
.\gradlew.bat bootRun        # Windows
./gradlew bootRun            # macOS / Linux
```

Le serveur démarre sur **http://localhost:8080**. Le premier lancement est plus long, le temps de télécharger les dépendances.

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
| `GET` | `/api/utilisateurs` | Lister les utilisateurs | `200`, `401` |
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
{ "id": 1, "expediteurId": 1, "destinataireId": 2, "contenu": "Bonjour !", "dateEnvoi": "2026-09-21T10:15:30" }
```

Une collection Postman documentée et testée est fournie dans [`postman/`](postman/README.md).

## Tester depuis un téléphone

Disponible avec les clients web (en cours).

1. Le téléphone et le PC sont sur le même réseau Wi-Fi.
2. Récupérer l'adresse IPv4 du PC avec `ipconfig`.
3. Autoriser le port 8080 dans le pare-feu Windows (réseau privé).
4. Ouvrir `http://<IPv4 du PC>:8080` sur le téléphone.

## Technologies

Java 21, Spring Boot 4.1 (Web MVC, WebSocket, Security, Data JPA, Validation), Gradle 9, JWT (jjwt), H2 et PostgreSQL, Lombok, HTML / CSS / JavaScript.

## Structure du projet

```
API-et-Web-Services/
├── README.md
├── Projet_Messagerie_Instantanee.pdf        sujet
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
        │   ├── security/                    SecurityConfig, JwtService, JwtAuthFilter
        │   ├── service/                     logique métier
        │   ├── repository/                  accès à la base (Spring Data JPA)
        │   ├── model/                       entités Utilisateur et Message
        │   └── dto/                         objets JSON échangés (records)
        ├── main/resources/
        │   ├── application.properties           configuration par défaut (H2)
        │   └── application-postgres.properties  profil PostgreSQL
        └── test/                            tests JUnit (H2 en mémoire)
```

---

Projet réalisé en deuxième année du cycle ingénieur Big Data & Machine Learning, EFREI.
