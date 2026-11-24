# Guide d’utilisation — ZERNEX Files 1.1.0

Ce guide décrit les actions réellement intégrées dans la branche 1.1.0. Les commandes restent locales : aucun fichier n’est envoyé vers un serveur ZERNEX.

## 1. Première ouverture et accès au stockage

### Android 11 à Android 16 — variante play
1. Installer l’APK play.
2. À l’écran d’accès, choisir **Gérer tous les fichiers** et activer l’autorisation pour ZERNEX Files dans les paramètres Android.
3. Revenir dans l’application. Le navigateur affiche le stockage partagé principal.
4. Pour limiter l’accès à un dossier précis, ouvrir le menu de stockage et choisir le mode SAF. Sélectionner le dossier dans le sélecteur Android et autoriser l’accès persistant.

### Android 10 — variante android10Legacy
1. Installer l’APK android10Legacy prévu pour une distribution directe.
2. Accorder les autorisations de lecture et d’écriture demandées.
3. Le navigateur démarre dans le stockage partagé.
4. La variante Play moderne ne contourne pas le stockage cloisonné d’Android 10 : elle utilise SAF et reste limitée au dossier choisi.

Ne sélectionne pas / comme si c’était la racine de tes fichiers personnels. Android continue de protéger les données privées des applications et les répertoires système.

## 2. Naviguer, sélectionner et agir

- Appuie une fois sur un dossier pour l’ouvrir.
- Utilise le fil d’Ariane ou la flèche de remontée pour revenir.
- Appuie longuement sur un fichier ou un dossier pour commencer la sélection multiple.
- Une fois la sélection active, touche les autres éléments pour les ajouter ou les retirer.
- La barre d’actions groupées permet de copier, couper, renommer, mettre à la corbeille, partager, afficher les détails et compresser.
- Pour coller un lot copié ou coupé, ouvre le dossier de destination puis touche **Coller**. Les collisions de noms produisent un nom libre ; les éléments existants ne sont pas silencieusement remplacés.

## 3. Créer une archive

1. Sélectionne un ou plusieurs fichiers/dossiers par appui long.
2. Touche **Compresser** dans la barre d’actions.
3. Choisis le format : ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2 ou TAR.XZ.
4. Pour ZIP uniquement, tu peux saisir un mot de passe d’au moins 8 caractères. L’archive ZIP est alors créée avec AES-256.
5. Appuie sur **Créer**. L’archive est construite dans le dossier de destination configuré. L’application écrit d’abord un fichier temporaire et ne publie le nom final qu’après succès.
6. Les originaux ne sont déplacés vers la corbeille qu’après création réussie et uniquement si l’option correspondante est activée dans les paramètres.

**À savoir :** 7Z, TAR, TAR.GZ, TAR.BZ2 et TAR.XZ sont créés sans mot de passe dans cette version. TAR ne possède pas de chiffrement natif dans ce flux.

## 4. Ouvrir et extraire une archive

1. Touche un fichier ZIP, 7Z, TAR, TAR.GZ, TGZ, TAR.BZ2, TBZ, TBZ2, TAR.XZ, TXZ, GZ, BZ2 ou XZ.
2. L’écran d’archive liste les entrées lorsque le format le permet.
3. Touche **Extraire ici**.
4. Pour une archive protégée, saisis le mot de passe demandé.
5. L’extraction est placée dans un nouveau dossier portant le nom de l’archive (sans extension). Aucun fichier existant n’est écrasé.

Garde-fous actuels de l’extracteur : maximum 10 000 entrées, 256 Mio par fichier et 512 Mio au total. Les chemins absolus, les chemins contenant .., les entrées dupliquées, les liens et les fichiers TAR spéciaux sont refusés. Si une extraction échoue, les éléments créés par cette opération sont supprimés au mieux.

Les fichiers simples GZ, BZ2 et XZ sont décompressés sous le nom sans extension dans le dossier créé.

## 5. Autres outils déjà présents

- Recherche dans le dossier et recherche avancée locale.
- Affichage liste/grille, tri, fichiers cachés, favoris, récents et dossier de démarrage.
- Création de dossiers et de fichiers texte.
- Éditeur texte local avec sauvegarde contrôlée.
- Corbeille locale, restauration, propriétés, tailles, MD5 et recherche de doublons.
- Aperçus image, PDF, audio et vidéo ; inspection de certains keystores et certificats.
- En mode SAF : navigation, recherche, favoris, copie/déplacement, renommage, corbeille, propriétés, éditeur et opérations ZIP disponibles chez le fournisseur choisi.

## 6. Capacités non promises dans 1.1.0

| Fonction | État |
|---|---|
| ZIP : lecture, création, extraction | Intégré en mode local ; ZIP existant en SAF |
| ZIP protégé AES-256 | Création/extraction en mode local |
| 7Z : lecture, création, extraction | Intégré en mode local ; mot de passe 7Z accepté à l’extraction |
| TAR / TAR.GZ / TAR.BZ2 / TAR.XZ | Lecture, création et extraction en mode local |
| GZ / BZ2 / XZ simples | Décompression en mode local |
| RAR / RAR5, ISO, CAB, ARJ, ZST | Non pris en charge |
| Archives multi-volumes, réparation et modification d’archive en place | Non pris en charge |
| Même moteur d’archives avancé en SAF | Pas encore intégré |
| Onglets et double panneau | Pas encore intégrés |
| SFTP / SMB / WebDAV / FTP | Pas encore intégrés |
| File de transferts persistante avec pause/reprise | Pas encore intégrée |
| Shizuku / root | Non intégré ; aucune élévation cachée |

## 7. Que faire en cas d’échec ?

- **Accès refusé** : ouvre Paramètres Android → Applications → ZERNEX Files → Accès spécial, puis contrôle l’autorisation appropriée. En SAF, sélectionne de nouveau le dossier.
- **Archive invalide ou mot de passe incorrect** : vérifie le mot de passe et teste le fichier avec une copie de sauvegarde ; l’application n’essaie pas de réparer les archives.
- **Espace insuffisant** : libère de l’espace dans la destination ; l’extraction n’écrase pas des fichiers pour libérer de la place.
- **Format non pris en charge** : ne renomme pas simplement l’extension ; un fichier RAR ne devient pas ZIP en changeant son suffixe.

## 8. Validation avant publication

La compilation CI, les tests automatiques d’archives, le lint et la construction des deux variantes sont requis. Les opérations SAF doivent aussi être testées sur un fournisseur local et une carte SD réelle. La réussite de la CI ne remplace pas les essais sur téléphone.
