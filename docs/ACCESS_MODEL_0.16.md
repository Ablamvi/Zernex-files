> Document historique de développement. Pour les règles de livraison actuelles, voir `../README.md`, `AUDIT_HISTORIQUE_0.14-0.19.md` et `TEST_PLAN_1.0.1.md`.

# ZERNEX Files — modèle d’accès 0.16.1

## Cible et exigences

- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 23`.
- API 36 est requis pour les nouvelles applications et mises à jour Google Play depuis le 31 août 2026.
- `requestLegacyExternalStorage` est supprimé : il est ignoré sur Android 11+ et ne peut pas être utilisé comme stratégie de stockage d’une application moderne.

## Permissions

- `MANAGE_EXTERNAL_STORAGE` : accès étendu spécial sur Android 11+ uniquement après activation explicite par l’utilisateur dans les paramètres Android.
- `READ_EXTERNAL_STORAGE` (max SDK 32) : permission historique, pas un accès universel aux fichiers avec une cible moderne.
- `WRITE_EXTERNAL_STORAGE` (max SDK 29) : permission historique, sans effet d’accès global sur les versions récentes.
- Android 10 avec `targetSdk 36` reste soumis au stockage cloisonné. Pour un explorateur complet sur Android 10, il faut intégrer un fournisseur SAF basé sur `ACTION_OPEN_DOCUMENT_TREE` et utiliser les URI `DocumentFile` pour les opérations sur les fichiers sélectionnés.

## Racine de navigation

L’écran démarre dans le volume partagé principal (`Environment.getExternalStorageDirectory()`), pas dans `/`. La racine `/` est la racine Linux du système, pas la racine des fichiers personnels. Le fait de la définir comme point de départ ne confère aucun privilège root et produit des erreurs d’accès sur les zones protégées.

## Limites système

Les données privées des autres applications, certaines zones de `Android/data`, `Android/obb`, les partitions système et les répertoires protégés par SELinux ne deviennent pas accessibles par une simple permission de stockage.
