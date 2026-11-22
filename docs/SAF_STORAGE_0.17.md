> Document historique de développement. Pour les règles de livraison actuelles, voir `../README.md`, `AUDIT_HISTORIQUE_0.14-0.19.md` et `TEST_PLAN_1.0.1.md`.

# ZERNEX Files 0.17.0 — stockage SAF

## Pourquoi ce mode existe

L'explorateur historique s'appuie sur `java.io.File` et sur l'accès au stockage partagé. Sur Android 10 (API 29) avec `targetSdk 36`, les anciennes permissions `READ_EXTERNAL_STORAGE` et `WRITE_EXTERNAL_STORAGE` ne permettent pas un accès général au stockage. ZERNEX utilise donc le Storage Access Framework pour ce système.

## Sélection et conservation du dossier

- L'application lance `ACTION_OPEN_DOCUMENT_TREE` via `ActivityResultContracts.OpenDocumentTree`.
- Elle conserve les autorisations de lecture et d'écriture persistantes retournées par le sélecteur.
- L'URI de l'arborescence et les segments de navigation sont sauvegardés dans les préférences locales.
- Les documents sont listés et gérés avec `DocumentFile`, et les contenus sont lus/écrits via `ContentResolver`.
- Sur Android 11+, le mode SAF est facultatif ; l'accès direct reste disponible après octroi de `MANAGE_EXTERNAL_STORAGE`.

## Fonctions implémentées dans le mode SAF

- Navigation dans l'arborescence choisie, fil d'Ariane cliquable, navigation Retour.
- Affichage liste/grille, dossiers avant fichiers, tri par nom/taille/date/type.
- Masquage/affichage des noms cachés, filtres de catégorie et recherche par nom.
- Recherche récursive limitée à 10 000 éléments pour éviter une exploration sans borne.
- Favoris de dossier, multi-sélection, ouverture avec application externe et partage.
- Création de dossier et de fichier texte, renommage, suppression après confirmation.
- Copie récursive et déplacement (copie puis suppression de l'original), avec résolution automatique des collisions de noms.

## Restrictions et limites

- Le SAF ne donne accès qu'au dossier choisi et à ses descendants ; il ne contourne pas les restrictions Android.
- À partir d'Android 11, le sélecteur interdit notamment la racine du stockage interne, les racines de volumes SD jugés fiables, `Download`, `Android/data` et `Android/obb`.
- Le mode SAF ne partage pas encore le moteur local de corbeille, les aperçus internes, la compression ZIP, les métadonnées avancées ou la file d'opérations avec progression et annulation.
- La suppression SAF n'est pas récupérable par la corbeille ZERNEX dans cette version.
- Les performances dépendent du fournisseur de documents du téléphone ; les fournisseurs distants ou très volumineux peuvent être lents.

## Tests à effectuer sur appareil

1. Android 10 : sélectionner le stockage partagé ou un sous-dossier, fermer et rouvrir l'application, vérifier que l'accès persiste.
2. Naviguer dans plusieurs sous-dossiers, revenir en arrière, utiliser le fil d'Ariane et relancer l'application.
3. Tester liste/grille, tri, recherche simple et recherche récursive sur un dossier de test.
4. Créer dossier/fichier texte, renommer, copier, coller, déplacer, partager et ouvrir un fichier avec une application externe.
5. Vérifier les cas de nom déjà existant, dossier déplacé vers un descendant, autorisation révoquée et dossier retiré.
6. Android 11+ : tester la sélection SAF d'un dossier autorisé et vérifier que les emplacements bloqués sont expliqués par Android.

L'implémentation ne doit être considérée validée qu'après ces essais sur de vrais appareils et fournisseurs de documents.
