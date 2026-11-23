# Recherche open source — ZERNEX Files 2.0

Date de consultation : 23 novembre 2026.

| Projet | Licence publiée | Référence examinée | Décision |
|---|---|---|---|
| Material Files — https://github.com/zhanghai/MaterialFiles | GPL-3.0-or-later | Navigation, fil d'Ariane, stockage, robustesse | Référence UX/architecture ; aucun code GPL copié |
| Amaze File Manager — https://github.com/TeamAmaze/AmazeFileManager | GPL-3.0 | Onglets, favoris, recherche, actions, gestionnaire d'apps | Référence fonctionnelle ; aucun code GPL copié |
| Prism File Explorer — https://github.com/Raival-e/Prism-File-Explorer | GPL-3.0 | Compose et organisation des vues | Référence Compose ; aucun code GPL copié |
| Strata — https://github.com/CluvexStudio/Strata | AGPL-3.0 | Double panneau, archives, réseau, coffre | Référence de périmètre ; aucun code AGPL copié |
| MTExplorer — https://github.com/r0ld3x/MTExplorer | MIT | Modèle de fichier, états de panneaux, barre d'actions | Référence de code compatible ; aucun fichier repris tel quel |

## Décision de licence

Le dépôt ZERNEX Files est Apache-2.0. Copier des fichiers de projets GPL/AGPL obligerait à analyser et respecter leurs conditions de redistribution, incompatibles avec une simple reprise sous Apache-2.0. Nous avons étudié leurs comportements et leurs architectures, sans copier leur code. MTExplorer est sous MIT, mais cette tranche n'en reprend pas non plus le code. Les composants UI restent basés sur les dépendances AndroidX/Compose déjà présentes.

## Choix retenus

- Classification sémantique partagée entre navigateur local et SAF.
- Palette centrale stricte et icônes au premier plan.
- Actions groupées et navigation guidées par icônes.
- Séparation progressive entre moteur d'opérations, navigation, rendu et préférences.
- Toute fonction annoncée doit être implémentée et testée, sinon elle reste listée comme non disponible.


## Vérification RAR

Junrar a été examiné comme candidat RAR/RAR5. Son README annonce RAR jusqu'à v7, mots de passe et archives multi-volumes, mais indique explicitement que son code ne peut pas servir à développer un archiveur compatible RAR/WinRAR. ZERNEX Files n'intègre donc pas Junrar ; l'ajouter sans avis juridique clair serait une mauvaise décision. La prise en charge RAR reste absente jusqu'à sélection d'un moteur dont la licence autorise explicitement cet usage. Référence : https://github.com/junrar/junrar
