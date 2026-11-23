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


## Fichiers source effectivement lus

Les dépôts ont été parcourus au niveau des implémentations, pas seulement de leurs pages de présentation.

### Material Files (GPL-3.0)

- `app/src/main/java/me/zhanghai/android/files/file/FileItem.kt` : modèle de fichier enrichi (MIME, attributs, état caché, collation et liens symboliques).
- `app/src/main/java/me/zhanghai/android/files/filelist/FileListViewModel.kt` : séparation de l'historique de navigation, de la recherche, du tri et de la liste.
- `app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt` : adaptation liste/grille et mise à jour différentielle.

**Idées retenues :** modèle de fichier unique et séparation de l'état de navigation. Aucun code copié.

### Amaze File Manager (GPL-3.0)

- `app/src/main/java/com/amaze/filemanager/ui/fragments/data/MainFragmentViewModel.kt` : état du chemin, tri et préférences.
- `app/src/main/java/com/amaze/filemanager/adapters/RecyclerAdapter.java` : rendu des entrées et classification de types/extensions.

**Idées retenues :** garder le tri et les préférences indépendants du rendu ; traiter les archives comme une catégorie fonctionnelle. Aucun code copié.

### Prism File Explorer (GPL-3.0)

- `app/src/main/java/com/raival/compose/file/explorer/screen/main/tab/files/FilesTab.kt` : orchestration Compose, sélection, tâches et barre d'actions.
- `app/src/main/java/com/raival/compose/file/explorer/screen/main/tab/files/holder/LocalFileHolder.kt` : abstraction des fichiers locaux et actions d'ouverture.
- `app/src/main/java/com/raival/compose/file/explorer/screen/main/MainActivityState.kt` : état central des onglets et de la navigation.

**Idées retenues :** modèle d'entrée séparé de la présentation et état d'écran explicite. Aucun code copié.

### Junrar

Le dépôt annonce la lecture jusqu'à RAR7, mots de passe et volumes multiples, mais son README interdit l'usage du code pour développer un archiveur compatible RAR/WinRAR. Nous ne l'intégrons pas. RAR/RAR5 reste donc une fonction non prise en charge jusqu'à validation d'un autre moteur et de sa licence.
