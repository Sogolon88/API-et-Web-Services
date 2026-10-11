# Collection Postman de ChatAPI

`ChatAPI.postman_collection.json` regroupe toutes les requêtes REST de l'API, avec leur documentation et des tests automatiques.

## Importer

1. Ouvrir Postman, puis **Import** (en haut à gauche).
2. Glisser le fichier `ChatAPI.postman_collection.json`.
3. La collection **ChatAPI - Messagerie instantanée** apparaît dans *Collections*.

Aucun environnement à importer : les variables (`baseUrl`, jetons, identifiants) sont stockées dans la collection elle-même.

## Utiliser

1. Démarrer le serveur : `cd chatapi` puis `.\gradlew.bat bootRun`.
2. Lancer le dossier **1. Comptes et authentification** : il crée Seyni et Fode, les connecte et enregistre leurs jetons.
3. Jouer les autres requêtes une à une, ou toute la collection avec **Run collection**.

La documentation de chaque requête (rôle, corps, codes de réponse, choix de sécurité) s'affiche dans le panneau **Documentation** de Postman (icône de page à droite).

## Organisation

| Dossier | Contenu |
|---|---|
| 1. Comptes et authentification | Inscription et connexion de Seyni et de Fode, enregistrement des JWT |
| 2. Utilisateurs | Liste des comptes (vue publique, sans mot de passe) |
| 3. Messages | Historique de la conversation vu par Seyni puis par Fode |
| 4. Sécurité et cas d'erreur | 401 sans jeton ou jeton falsifié, 401 au login, 409 doublon, 400 validation |

Le WebSocket temps réel (`/ws/messages?token=<JWT>`) ne peut pas être enregistré dans un fichier de collection : il se teste avec une requête **WebSocket** créée à la main dans Postman, ou avec les clients web.
