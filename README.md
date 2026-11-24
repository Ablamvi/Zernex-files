# ZERNEX Files 1.1.0

Gestionnaire de fichiers Android avec navigation locale/SAF, opérations groupées, corbeille, outils intégrés et moteur d’archives multi-formats. Application locale : les fichiers ne sont pas envoyés vers un service ZERNEX.

**Guide pas à pas :** docs/GUIDE_UTILISATION_FR_1.1.0.md  
**Matrice stricte des capacités et limites :** docs/FEATURES_AND_LIMITS_1.1.0.md

## Archives 1.1.0 — moteur local

- Lecture, création et extraction : ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2 et TAR.XZ.
- Décompression simple : GZ, BZ2 et XZ.
- Création ZIP chiffrée AES-256 avec mot de passe d’au moins 8 caractères.
- Mot de passe d’extraction ZIP et 7Z.
- Aperçu des entrées et extraction dans un nouveau dossier.
- Extraction sans écrasement, validation stricte des chemins, refus des liens/fichiers spéciaux, nettoyage des éléments partiels et plafonds anti-archive-bombe.
- RAR/RAR5, ISO, CAB, ARJ, ZST, archives multi-volumes et édition en place ne sont pas pris en charge dans cette version.

Le moteur avancé d’archives est disponible dans le navigateur de stockage local. Le mode SAF conserve actuellement ses actions ZIP ; il n’y a pas encore de parité multi-format entre les deux moteurs.

## Variantes Android

### play — distribution Google Play
- targetSdk = 36.
- Android 11+ : accès direct après activation explicite de **Gérer tous les fichiers** ; SAF reste disponible pour un dossier sélectionné.
- Android 10 : accès SAF limité au dossier choisi. La variante moderne ne contourne pas le stockage cloisonné.

### android10Legacy — APK de distribution directe
- targetSdk = 29, requestLegacyExternalStorage=true.
- Destinée à Android 10 uniquement et à la distribution hors Google Play.
- Accès direct après autorisations classiques.

## Compilation

Environnement requis : JDK 17, Android SDK API 36 et Build Tools 36.0.0.

- Tests unitaires : ./gradlew :app:testPlayDebugUnitTest :app:testAndroid10LegacyDebugUnitTest
- Lint et deux compilations : ./gradlew :app:testPlayDebugUnitTest :app:testAndroid10LegacyDebugUnitTest :app:lintPlayRelease :app:lintAndroid10LegacyRelease :app:assemblePlayRelease :app:assembleAndroid10LegacyRelease

Les APK sont générés sous app/build/outputs/apk/play/release/ et app/build/outputs/apk/android10Legacy/release/.

La signature Release est optionnelle localement et fournie par les variables ZERNEX_KEYSTORE_FILE, ZERNEX_KEYSTORE_PASSWORD, ZERNEX_KEY_ALIAS, ZERNEX_KEY_PASSWORD. Conserver le certificat privé d’origine pour les mises à jour ; ne jamais commiter le keystore.

## Fonctionnalités déjà intégrées

- Navigation, fil d’Ariane, recherche simple/avancée, tri, filtres et dossiers cachés.
- Favoris, récents, dossier de démarrage, vues liste/grille.
- Sélection multiple, renommage groupé, copier/couper/coller, partage et gestion des collisions.
- Création de fichiers/dossiers, éditeur texte, corbeille et restauration.
- Aperçus image, PDF, audio et vidéo.
- Propriétés, MD5, taille des dossiers, recherche de doublons, inspection de certificats et keystores.
- Navigateur SAF avec favoris, recherche, opérations de documents, corbeille, propriétés, éditeur et ZIP selon les capacités du fournisseur.

## Limites connues

Onglets, double panneau, transfert persistant avec pause/reprise, réseau SFTP/SMB/WebDAV, Shizuku/root, RAR/RAR5 et parité multi-format SAF ne sont pas encore intégrés. Les restrictions Android sur les données privées et les emplacements système restent applicables.

## Qualité et validation

La CI exécute le vérificateur statique, les tests unitaires du moteur d’archives, le lint Play et les deux compilations Release. La réussite de la CI ne remplace pas les essais manuels sur Android 10, Android 11+, carte SD et stockage USB.

Voir également CHANGELOG.md, docs/TEST_PLAN_1.0.1.md et docs/THIRD_PARTY_NOTICES.md.
