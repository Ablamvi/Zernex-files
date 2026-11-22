> Document historique de développement. Pour les règles de livraison actuelles, voir `../README.md`, `AUDIT_HISTORIQUE_0.14-0.19.md` et `TEST_PLAN_1.0.1.md`.

# ZERNEX Files — recherche, architecture et feuille de route

## Positionnement

ZERNEX Files est une application autonome (`com.zernex.files`), indépendante de ZERNEX Serveur. Elle doit devenir un explorateur et atelier local polyvalent, utilisable sans compte, sans publicité et sans transfert automatique des fichiers.

## Applications et outils étudiés

- **Material Files** : navigation claire, gestion d'archives, accès FTP/SFTP/SMB/WebDAV, liens symboliques et permissions Linux. Référence pour l'explorateur et les fournisseurs réseau. https://github.com/zhanghai/MaterialFiles
- **Amaze File Manager** : onglets, favoris, recherche, gestionnaire d'applications, archives, lecteur et éditeur intégrés. Référence pour les raccourcis et l'organisation des outils. https://github.com/TeamAmaze/AmazeFileManager
- **Butler** : onglets, recherche dans le contenu, corbeille, opérations avec indicateurs de progression, support Shizuku/root. Référence pour les opérations avancées et l'ergonomie power-user. https://github.com/d4rken-org/butler
- **Twig** : double panneau, comparaison/synchronisation, archives traitées comme des dossiers, SFTP/SMB/WebDAV/S3 et terminal. Référence pour la navigation multi-sources. https://f-droid.org/en/packages/com.twig.app/
- **Strata** : coffre chiffré, outils APK/DEX, éditeur de code, hex editor, partages réseau. Référence pour l'atelier développeur. Son code est AGPL-3.0 ; aucune reprise de code sans respecter cette licence. https://github.com/CluvexStudio/Strata
- **Android Storage Access Framework** : accès explicite aux documents et dossiers, autorisations persistantes et compatibilité avec stockage amovible. https://developer.android.com/guide/topics/providers/document-provider
- **Android apksigner / keytool** : chaîne officielle de signature et vérification APK. https://developer.android.com/tools/apksigner et https://developer.android.com/build/building-cmdline

## Architecture proposée

1. **UI** : Jetpack Compose Material 3, thème sombre/noir, navigation par onglets, adaptatif téléphone/tablette.
2. **Storage core** : interface unifiée `StorageProvider` avec implémentations SAF/local, stockage amovible, puis réseau. Ne pas contourner les restrictions Android ni demander `MANAGE_EXTERNAL_STORAGE` sans justification et consentement explicite.
3. **File operations** : moteur asynchrone avec progression, pause/annulation, vérification de taille/hash, opérations en lot, corbeille réversible et journal d'opérations.
4. **Archives** : module isolé avec formats, sécurité contre Zip Slip/path traversal, limites anti-bombe ZIP et extraction sélective.
5. **Editors/viewers** : éditeur texte/code, JSON/XML formatter, aperçu Markdown, hex viewer, images, PDF et métadonnées média.
6. **Developer lab** : Base64 texte/fichiers, hash SHA-256/SHA-512, inspecteur keystore/certificat, gestion APK, vérification et signature APK.
7. **Security vault** : secrets en mémoire le minimum de temps, déverrouillage biométrique facultatif, chiffrement authentifié des données de coffre avec Android Keystore. Base64 doit toujours être étiqueté comme encodage, jamais comme chiffrement.
8. **Network** : SFTP, SMB, FTP explicite, WebDAV ; connexions enregistrées chiffrées, secrets jamais affichés en clair.
9. **App tools** : inspecteur APK, informations package, extraction d'APK installés dans les limites Android, partage Wi-Fi temporaire et QR code.

## Feuille de route

### Phase 0 — Fondation 0.1.0 (présente)
- Projet Android indépendant, package et identité séparés.
- Explorateur SAF initial : choisir un dossier, lister, naviguer, ouvrir, renommer et supprimer.
- Convertisseur Base64 texte.
- Sélection et inspection initiale des keystores JKS/PKCS12 lorsque les fournisseurs du système le supportent.
- Workflow de release signé avec le certificat de l'utilisateur.

### Phase 1 — Gestionnaire de fichiers complet
- Copier/couper/coller, déplacer entre emplacements, multi-sélection, glisser-déposer.
- Barre de progression, annulation, reprise si possible, comparaison source/destination.
- Corbeille, favoris, historique, onglets, double panneau.
- Recherche rapide et recherche dans le contenu, tri et filtres.
- Permissions, propriétés, partage et renommage par lots.

### Phase 2 — Archives et édition
- ZIP/7z/TAR/TAR.GZ/GZIP/XZ ; RAR uniquement avec bibliothèque et licence appropriées.
- Parcourir une archive comme un dossier, extraire une sélection, créer une archive chiffrée si le format le permet.
- Éditeur texte/code avec coloration, gros fichiers, recherche/remplacement, sauvegarde atomique.
- JSON/XML/YAML/Markdown, visionneuse hex, PDF, image, audio et vidéo.

### Phase 3 — Atelier signature et APK
- Importer/exporter keystore, inspecter alias, type de clé, chaîne de certificats, dates et empreintes.
- Encodage/décodage Base64 de fichiers binaires, avec taille avant/après et garde-fous mémoire.
- Générer un nouveau keystore sur l'appareil, exporter certificat public, conversion JKS/PKCS12 lorsque sûre.
- Inspecter APK/AAB, manifeste, version, permissions, ABI, certificats et vérification.
- Signature APK locale via bibliothèque apksig ; l'AAB nécessite une chaîne adaptée (jarsigner/bundletool) et sera séparée.
- Ne jamais modifier directement les octets d'un keystore : toute modification passe par une opération de conversion/import-export validée.

### Phase 4 — Réseau et avancé
- SFTP, SMB, WebDAV, FTP, stockage cloud via connecteurs facultatifs.
- Transfert local Wi-Fi avec serveur temporaire, code QR, expiration et confirmation.
- Shizuku en option pour les fonctions autorisées ; root facultatif et jamais requis pour l'usage normal.
- Synchronisation et comparaison de dossiers, règles de sauvegarde.

### Phase 5 — Qualité et publication
- Tests unitaires et instrumentation, tests de corruption d'archives et de permissions.
- Vérification APK avec `apksigner verify`, empreinte de certificat de release attendue, checksums SHA-256.
- APK/AAB signés par le keystore de l'utilisateur, builds reproductibles autant que possible.
- Tests réels sur Android 10 ARMv7 et appareils ARM64 avant de déclarer les fonctions validées.

## Décisions de sécurité

- La signature de l'application doit rester stable entre les mises à jour : garder le même certificat privé. Une autre signature empêchera la mise à jour directe de l'APK installé.
- Le keystore ne doit jamais être commité, inclus dans l'APK ou envoyé dans une conversation. Les secrets GitHub doivent rester dans les secrets du dépôt.
- Base64 est réversible et n'apporte aucune confidentialité.
- Les suppressions doivent être confirmées ; les opérations risquées devraient utiliser une corbeille avant suppression définitive.
- L'accès Android aux fichiers est accordé par l'utilisateur. Les restrictions de stockage du système ne doivent pas être contournées.


## Compléments de recherche (novembre 2026)

- **pFiles** (MIT) : référence très proche du besoin pour double panneau, vues liste/grille, barre de chemin, batch rename, archives ZIP/JAR/APK, éditeur multi-langage et visionneuse hex. Dépôt : https://github.com/vladgba/pFiles. La licence MIT permet une réutilisation sous réserve de conserver les notices ; aucune reprise de code n'est incluse dans cette préversion.
- **Sora Editor** (LGPL-2.1+) : candidat pour remplacer la coloration syntaxique légère par un véritable moteur d'édition Android, avec coloration incrémentale, auto-indentation, undo/redo, recherche/remplacement, TextMate et Tree-sitter. Dépôt : https://github.com/Rosemoe/sora-editor. L'intégration devra conserver les obligations LGPL et vérifier les modules natifs retenus.
- **Shizuku API** (Apache-2.0) : piste recommandée pour un fournisseur de stockage privilégié. Shizuku peut fonctionner sous identité ADB/shell ou root ; ADB ne peut pas lire les données privées de toutes les applications. Guide : https://github.com/RikkaApps/Shizuku-API. Il faut intégrer la liaison Binder, le consentement, la reconnexion et des opérations de fichiers dédiées ; ajouter seulement la dépendance ne suffit pas.
- **Media3 / ExoPlayer** : lecture vidéo/audio intégrée. Documentation officielle : https://developer.android.com/jetpack/androidx/releases/media3.
- **PdfRenderer Android** : aperçu PDF natif sans ajouter une bibliothèque lourde, avec prise en charge des pages via des descripteurs seekable. Documentation : https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.
- **Bouncy Castle** : fournisseur cryptographique complémentaire pour les formats keystore BKS et certains conteneurs PKCS. Dépôt : https://github.com/bcgit/bc-java ; licence Bouncy Castle.

### Décisions de licence et d'architecture

Le projet reste propriétaire de son identité et de son code applicatif ; les composants tiers sont intégrés comme dépendances et leurs licences/attributions devront figurer dans l'écran « À propos » et dans les notices de distribution. Le code GPL de Material Files/Butler et AGPL de Strata ne sera pas copié dans le projet sans décision explicite de passer le code dérivé sous les licences correspondantes.

### État 0.2.0-preview

Cette préversion ajoute la navigation par chemin local, `/` sous réserve des permissions du système, la vue liste/grille, le tri, la recherche courante, les opérations élémentaires, les favoris et chemin par défaut, la compression/extraction ZIP, les aperçus image/PDF/audio/vidéo, un éditeur texte/code à coloration de base, et l'inspection JKS/PKCS12/BKS/X.509. Elle ne doit pas être présentée comme un clone final de ZArchiver/MT Manager : les fournisseurs root/Shizuku, les archives 7z/RAR/TAR, le moteur Sora, les onglets, le double panneau, la corbeille, l'historique de transfert et les traductions complètes restent à réaliser.


## Intégration pratique des références — version 0.17.0

La comparaison des gestionnaires Material Files, Amaze File Manager et pFiles a guidé les éléments repris dans ZERNEX. Il s'agit d'une reprise des idées d'interface et de fonctionnement, pas d'une copie de leur code source :

- Material Files : fil d'Ariane cliquable, tri stable avec dossiers en premier, séparation du backend de stockage et de la présentation. Référence : https://github.com/zhanghai/MaterialFiles.
- Amaze : commandes de sélection multiple, favoris, accès rapide aux fichiers, partage et organisation des opérations. Référence : https://github.com/TeamAmaze/AmazeFileManager.
- pFiles : vues liste/grille, chemin cliquable, catégories de recherche et collision de noms gérée automatiquement lors des copies. Référence : https://github.com/vladgba/pFiles.
- Android SAF : sélection explicite d'une arborescence, autorisation persistante, opérations via DocumentFile/ContentResolver ; les restrictions système Android 11+ demeurent applicables. Référence : https://developer.android.com/training/data-storage/shared/documents-files.

Dans 0.17.0, le mode SAF couvre le noyau de navigation et de gestion des documents. Les moteurs d'aperçu, de corbeille, d'archives, de transfert asynchrone et les autres fonctions avancées ne sont pas encore unifiés entre `java.io.File` et SAF. La prochaine étape d'architecture est une abstraction `StorageEntry` / `StorageProvider` pour éviter la duplication de l'interface et garantir les mêmes capacités aux deux moteurs.
