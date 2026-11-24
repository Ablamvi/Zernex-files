# Changelog

## 1.1.0 — moteur d’archives professionnel (branche de validation)
- Renforcement de la CI : tests unitaires et lint exécutés pour Play et Android 10 Legacy.
- Tests supplémentaires pour les chemins ZIP absolus (Unix/Windows), les répertoires implicites et la préservation d’une destination non vide.
- Ajout d’un moteur local multi-format : ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2 et TAR.XZ ; décompression GZ/BZ2/XZ.
- Création ZIP chiffrée AES-256 facultative (mot de passe minimum 8 caractères) et extraction avec mot de passe ZIP/7Z.
- Ajout du choix du format, de l’aperçu du contenu et d’une extraction dans un nouveau dossier.
- Renforcement extraction : validation canonique des chemins, rejet des traversées de répertoires, doublons et liens TAR, absence d’écrasement, plafonds d’entrées/tailles et rollback.
- Ajout de tests de round-trip ZIP, ZIP AES-256, 7Z, TAR.GZ et attaque Zip Slip.
- Version de l’application portée à 1.1.0 ; build/test CI élargis.
- Limites explicites : RAR/RAR5, ISO, CAB, ARJ, ZST, multi-volumes, édition d’archives et parité multi-format SAF ne sont pas inclus.



## 1.0.4
- Navigation plus claire : bouton « dossier parent » dans l’en-tête, retour progressif et accès direct à la racine via le fil d’Ariane « Stockage ».
- Suppression des cases à cocher, boutons d’informations et menus à trois points sur chaque élément du navigateur local.
- Appui simple pour ouvrir un dossier ; appui long pour sélectionner un élément, puis sélection multiple par appuis longs ou taps pendant la sélection.
- Barre d’actions regroupée et ancrée en bas : copier, couper, renommer, corbeille, partager et compresser.
- La liste conserve une marge de défilement pour que les derniers fichiers restent accessibles au-dessus de la barre d’actions.

## 1.0.4
- Navigation parent/racine plus visible : flèche de remontée et accès direct à la racine par « Stockage » / « / » dans le fil d’Ariane.
- Retrait des cases à cocher et des boutons d’information / menus à trois points attachés à chaque élément du navigateur local.
- Appui simple pour entrer dans un dossier ; appui long pour sélectionner, puis sélection multiple par appuis supplémentaires.
- Regroupement des actions de sélection dans une barre inférieure : copier, couper, renommer, corbeille, partager et compresser.
- La même présentation par barre inférieure est appliquée au navigateur SAF.

## 1.0.3
- Renforcement du contraste des textes : blanc plus lumineux pour le texte principal et gris clair plus lisible pour les informations secondaires.
- Couleur de contenu par défaut forcée sur la couleur de texte du thème pour éviter les libellés sombres hérités dans les composants personnalisés.
- Conservation des couleurs d’accent et de l’identité visuelle sombre et colorée.

# ZERNEX Files — Journal des modifications

## 1.0.1 — Correctif Lint et écrans dédiés

- Correction de `NewApi` pour `android:windowLightNavigationBar` : l’attribut est déplacé dans `res/values-v27/styles.xml`, et retiré des ressources de base accessibles dès API 23.
- Pages dédiées avec barre de titre et retour pour l’éditeur, l’aperçu et les propriétés des fichiers ; paramètres SAF, favoris et corbeille ; récents, favoris, corbeille, doublons, stockage et autorisations en mode local.
- Les actions de création restent dans l’en-tête/menu ; suppression des boutons flottants qui masquaient le contenu. Les dialogues sont réservés aux confirmations et aux formulaires ponctuels.
- Sauvegarde éditeur : l’état « modifié » n’est effacé qu’après réussite de l’écriture ; confirmation de fermeture si des modifications ne sont pas enregistrées.
- Corbeille locale : les éléments dont la suppression définitive échoue restent affichés et le résultat partiel est signalé.
- Ajout du contrôle statique des écrans et de la qualification API 27.

## 1.0.0 — Refonte de base et accès stockage adapté à Android

- Reprise des fonctions existantes des versions 0.14.0 à 0.19.0 : navigation, chemin visible, recherche avancée, favoris, récents, tri, liste/grille, fichiers cachés, sélection multiple, renommage simple et groupé, presse-papiers copier/couper/coller, partage, corbeille, restauration, ZIP, aperçus, éditeur, détails, MD5, doublons et stockage.
- Retour à une identité visuelle colorée et plus soignée : fonds bleu nuit, surfaces bleutées, accents ambre/turquoise/violet et couleurs par catégories de fichiers. Suppression des derniers fonds et séparateurs gris-noir codés en dur dans le navigateur principal.
- Chemin courant et recherche restent visibles dans l'en-tête.
- Android 11+ : accès direct via `MANAGE_EXTERNAL_STORAGE` après validation dans les paramètres système ; SAF reste un mode optionnel.
- Android 10 variante `android10Legacy` : cible API 29, `requestLegacyExternalStorage=true` et permissions classiques ; l'utilisateur peut parcourir le volume partagé sans sélectionner un arbre SAF.
- Android 10 variante `play` : SAF comme solution de compatibilité, car la variante Play doit conserver une cible moderne.
- Ajout du guide de build, de l'audit comparatif des archives et d'une CI qui compile chaque variante.

### Matrice des variantes

| Variante | Target SDK | Android 10 | Android 11+ | Distribution |
|---|---:|---|---|---|
| `play` | 36 | SAF | Accès direct après autorisation, SAF optionnel | Google Play |
| `android10Legacy` | 29 | Accès direct via mode legacy + permissions classiques | Non destinée à ces appareils | APK direct Android 10 uniquement |

### Vérification de cette livraison

L'analyse statique et les validations de sources seront consignées dans `BUILD_STATUS.txt`. Aucun APK ne doit être annoncé comme testé sans compilation CI et test sur appareil physique.

## 1.0.2 — correction compilation SAF
- Ajout des composants Compose manquants `SafFileRow` et `SafFileCard` utilisés par les vues liste et grille du navigateur SAF.
- Ajout de l'import `androidx.compose.foundation.clickable` requis par les pages SAF (favoris et actions de navigation).
- Conservation du comportement de sélection : appui pour ouvrir ou basculer la sélection active, appui long pour sélectionner.
- Incrément de version à 1.0.2 (versionCode 1003).
