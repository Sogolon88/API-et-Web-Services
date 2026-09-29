# Messagerie instantanée entre deux appareils

Projet de synthèse du module **API & Web Services** ( Ingénierie Informatique, EFREI).

Cette application permet à deux utilisateurs, connectés depuis deux appareils différents (par exemple un téléphone et un ordinateur), de s'échanger des messages en temps réel. Les appareils ne communiquent jamais directement : tous les messages passent par un serveur central qui les enregistre puis les retransmet.
Real-time messaging application using REST API, WebSocket, JWT authentication, and JSON for secure and instant communication between multiple devices.

## Fonctionnalité:

- **API REST** : création de compte, connexion, liste des utilisateurs et historique des messages.
- **WebSocket** : envoi et réception des messages en direct, sans rafraîchir la page.
- **JWT** : chaque utilisateur s'authentifie et le serveur protège les endpoints sensibles.
- **PostgreSQL** : les comptes et les messages sont persistés côté serveur, donc l'historique reste disponible après une reconnexion.
- **Deux clients web** (HTML/JavaScript) : une page « mobile » et une page « bureau », servies par le serveur.

## Technologies

Java, Spring Boot (Web, WebSocket, Security, Data JPA), Gradle, PostgreSQL, JWT, HTML/CSS/JavaScript.

## Structure du projet:
```
API_Web_Services/
├── README.md
├── Projet_Messagerie_Instantanee.pdf      (sujet)
├── docs/
│   ├── rapport.pdf                        (rapport de 5 à 8 pages)
│   ├── architecture.png                   (schéma d'architecture)
│   └── demo.png                           (capture des deux appareils)
└── server/
    ├── build.gradle
    ├── settings.gradle
    ├── gradlew / gradlew.bat
    └── src/
        ├── main/
        │   ├── java/fr/efrei/chatapi/
        │   │   ├── chatApplication.java
        │   │   ├── config/
        │   │   │   ├── SecurityConfig.java        (règles JWT, routes publiques/protégées)
        │   │   │   └── WebSocketConfig.java       (déclare /ws/messages)
        │   │   ├── controller/
        │   │   │   ├── CompteController.java      (POST /api/comptes)
        │   │   │   ├── AuthController.java        (POST /api/auth/login)
        │   │   │   ├── UtilisateurController.java (GET /api/utilisateurs)
        │   │   │   └── MessageController.java     (GET /api/messages/{id})
        │   │   ├── websocket/
        │   │   │   ├── MessageWebSocketHandler.java (réception, sauvegarde, relais)
        │   │   │   ├── JwtHandshakeInterceptor.java (vérifie le JWT à la connexion)
        │   │   │   └── SessionRegistry.java         (table userId → session)
        │   │   ├── security/
        │   │   │   ├── JwtService.java            (création et validation du jeton)
        │   │   │   └── JwtAuthFilter.java         (filtre des requêtes REST)
        │   │   ├── service/
        │   │   │   ├── UtilisateurService.java
        │   │   │   └── MessageService.java
        │   │   ├── repository/
        │   │   │   ├── UtilisateurRepository.java
        │   │   │   └── MessageRepository.java
        │   │   ├── model/
        │   │   │   ├── Utilisateur.java           (entité)
        │   │   │   └── Message.java               (entité)
        │   │   └── dto/                           (objets JSON échangés)
        │   └── resources/
        │       ├── application.properties         (base, port, secret JWT)
        │       └── static/                        (les clients)
        │           ├── mobile.html
        │           ├── desktop.html
        │           ├── css/
        │           │   ├── mobile.css
        │           │   └── desktop.css
        │           └── js/
        │               └── chat.js                (login, fetch, WebSocket : partagé)
        └── test/
```

## Projet développé en 2ème année du cycle ingénieur Big Data & Machine Learning - EFREI