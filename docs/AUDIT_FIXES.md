# ZERNEX Files 0.2.0-preview — rapport d'audit

## Changements de cette préversion

- Remplacement de l'ancien navigateur SAF limité à un dossier par un explorateur à chemin absolu avec fil d'Ariane et navigation vers `/` lorsque le système autorise la lecture.
- Ajout de la demande d'accès Android « gérer tous les fichiers » (API 30+) et de la demande de permission de stockage classique sur les versions plus anciennes.
- Ajout des vues liste/grille, tri, recherche dans le dossier courant, affichage des fichiers cachés, sélection multiple, copier/couper/coller, renommage, suppression, création de dossiers, propriétés et partage.
- Ajout des favoris et du chemin de démarrage configurable.
- Ajout de la création et de l'extraction ZIP avec validation canonique des chemins, plafond d'entrées et limite de données extraites.
- Ajout d'aperçus image via Coil, PDF via `PdfRenderer`, vidéo/audio via Media3.
- Ajout d'un éditeur de texte/code avec coloration de base pour les syntaxes les plus courantes, limite de taille en édition et écriture via fichier temporaire.
- Ajout de l'inspection de JKS, PKCS12, JCEKS, BKS et des certificats X.509, avec empreintes SHA-256 et informations de chaîne/certificat. Bouncy Castle est utilisé pour BKS.
- Interface ramenée à une palette principalement noire, blanche et grise.
- `compileSdk = 36`, `targetSdk = 35`, `minSdk = 23` conservés. Le workflow produit l'APK debug sans secrets et les artefacts Release uniquement si les secrets de signature sont présents.

## Vérifications réalisées dans l'environnement de préparation

- XML du manifeste et des chemins FileProvider analysé.
- Workflow YAML chargé par un parseur YAML.
- Analyse syntaxique Kotlin sans erreur de parsing détectée par le compilateur Kotlin local. Le classpath Android/Compose n'est pas disponible ici, cette vérification ne constitue donc pas une compilation Android.
- Tentative de compilation Gradle effectuée ; elle a échoué avant la configuration du projet car `services.gradle.org` ne pouvait pas être résolu par DNS dans cet environnement.

## Limites restantes avant une version premium stable

- Root/Shizuku n'est pas encore intégré. L'accès à `/` ne donne pas les droits de lecture sur `/data`, les données privées d'autres applications ou les partitions protégées.
- Archives 7z/RAR/TAR, ZIP chiffré, navigation interne dans les archives, et contrôles avancés de bombes d'archives restent à ajouter.
- L'éditeur actuel est une base à coloration syntaxique simple ; Sora Editor/Tree-sitter/TextMate, lignes, onglets, recherche/remplacement avancée, LSP et fichiers volumineux restent à intégrer.
- Les opérations de transfert n'ont pas encore une file persistante avec progression détaillée, pause/reprise et annulation fiable.
- La corbeille, l'historique, les onglets, le double panneau, la recherche récursive, les connexions réseau, le gestionnaire APK et les sauvegardes planifiées restent à réaliser.
- Les traductions complètes de l'interface ne sont pas encore livrées ; le français est la langue de cette préversion.
- Aucun build Android complet ni test sur appareil n'a pu être confirmé dans cet environnement. Le workflow GitHub Actions doit confirmer la compilation sur un runner.

## Version 0.3.0-preview — permissions and safer file operations

- Kept the Android all-files access special permission flow for Android 11+ and legacy storage runtime permissions on Android 6–10.
- Improved create-folder and rename validation (blank names, path separators, dot segments, and existing destinations are rejected).
- Reworked deletion confirmation to list selected items and warn that directory contents are deleted recursively.
- Prevented deleting the shared-storage root, the currently browsed directory, or an ancestor containing the current directory.
- Removed deleted paths from stored favorites and report partial deletion failures instead of silently reporting a generic result.
- Bumped `versionCode` to 3 and `versionName` to `0.3.0-preview`.

Note: `MANAGE_EXTERNAL_STORAGE` is a special Android access setting, not a normal runtime permission. Google Play distribution requires eligibility and approval for broad file-management access. Android still restricts protected app-private and some system locations.

## Renforcement 0.14.0

- L'extraction ZIP est exécutée sur `Dispatchers.IO`, et non sur le thread UI.
- Les noms d'entrées sont normalisés et les chemins canoniques doivent rester strictement sous le dossier de destination.
- Les chemins absolus, chemins Windows, entrées dupliquées, conflits et dépassements de limites sont refusés.
- La destination doit être vide ; aucune entrée existante n'est écrasée.
- Les fichiers partiels créés par une extraction échouée sont supprimés au mieux.
- La compression utilise un fichier `.part`, puis le renomme après succès. Les liens symboliques et les destinations situées dans un dossier source sont refusés.

Limites restantes : les opérations générales de copie/déplacement et le stockage reposent encore largement sur `java.io.File` et l'accès étendu ; une migration SAF doit être menée séparément avec tests sur appareils réels. Les tests automatisés et la compilation complète restent à exécuter.


## Correctif complémentaire — formatage des tailles

- Ajout de `formatSize(Long)` utilisé par l'explorateur, les propriétés, les résultats de recherche, les doublons et l'inspection des archives.
- Les valeurs négatives sont affichées comme « Taille inconnue » ; les tailles positives sont formatées en octets, Ko, Mo, Go, To, Po ou Eo avec une décimale et la locale de l'appareil.

## Correctif 0.16.0 — compatibilité stockage et navigation racine

- La cible APK passe à Android 10 / API 29 avec `requestLegacyExternalStorage="true"`, car le moteur existant est majoritairement fondé sur `java.io.File` et l'ancien couple de permissions ne fournissait plus l'accès attendu sous targetSdk 35 sur Android 10.
- `READ_EXTERNAL_STORAGE` et `WRITE_EXTERNAL_STORAGE` ne sont plus limités à une ancienne version d'Android dans le manifeste.
- Première ouverture migrée vers `/`; un ancien chemin sauvegardé qui n'existe plus est remplacé par `/`.
- Le diagnostic du dossier courant distingue chemin absent, non-dossier, lecture refusée, sécurité Android et dossier réellement vide.
- Suppression via corbeille refusée pour les racines et emplacements système critiques.
- C'est un choix de compatibilité pour APK autonome. Android garde les protections root/SELinux ; la cible API 29 ne convient pas à une publication Google Play moderne sans variante distincte et revue de politique.
- Compilation Android complète et test appareil non confirmés dans cet environnement.


## Correctif de compilation 0.16.1
- `targetSdk` relevé de 29 à 36 pour corriger `ExpiredTargetSdkVersion` dans `lintVitalRelease` et satisfaire la politique Google Play en vigueur pour les nouvelles soumissions.
- Suppression de `requestLegacyExternalStorage` et plafonnement des permissions historiques à leurs API pertinentes.
- `hasStorageReadAccess()` n’assimile plus READ_EXTERNAL_STORAGE à l’accès global sur Android 10+ : Android 11+ vérifie `Environment.isExternalStorageManager()`, Android 10 signale explicitement le besoin d’un accès SAF.
- La racine de départ devient le volume partagé principal ; le gestionnaire de retour utilise cette racine réelle.
- Limite restante : le fournisseur SAF complet pour Android 10 n’est pas encore implémenté.
