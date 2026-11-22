# Audit des archives ZERNEX Files 0.14.0 à 0.19.0

## Sources réellement retrouvées

| Version | Archive retrouvée | Apport relevé dans le code et les notes |
|---|---|---|
| 0.14.0 | Plusieurs variantes correctives : `corrige-kotlin`, `corrige-permission`, archive APK-seulement | Fiabilisation ZIP, validation des chemins d'extraction, limites d'extraction, création ZIP via fichier temporaire. Base du navigateur local. |
| 0.15.0 | `ZERNEX-Files-v0.15.0-source.zip` | Refonte sombre monochrome, suppression du chemin de l'en-tête et déplacement de raccourcis dans les paramètres. Cette direction visuelle est abandonnée en 1.0.0. |
| 0.16.0 | `ZERNEX-Files-v0.16.0-source.zip` | Cible Android moderne, modèle d'accès au stockage et correction des chemins de démarrage. |
| 0.16.1 | `ZERNEX-Files-v0.16.1-source.zip` | Mise à jour de cible/SDK et adaptation des permissions ; le code Android 10 renvoyait encore systématiquement un accès refusé. |
| 0.16.2 | `ZERNEX-Files-v0.16.2-source.zip` | Retour du chemin visible en haut et ajout de l'icône de recherche. |
| 0.17.0 | `ZERNEX-Files-v0.17.0-source.zip` | Ajout d'un deuxième navigateur SAF basé sur `DocumentFile`, sans moteur commun avec `java.io.File`. |
| 0.18.0 | **Archive absente de la bibliothèque consultée** | Impossible d'inspecter cette version. Aucun changement n'est inventé ou attribué à 0.18.0. |
| 0.19.0 | `ZERNEX-Files-v0.19.0-source.zip` | Cases de sélection, opérations groupées, renommage simple et multiple, garde-fous de transferts et SAF enrichi. |

Les archives 0.14.0, 0.15.0, 0.16.0, 0.16.1, 0.16.2, 0.17.0 et 0.19.0 ont été ouvertes et comparées. Les variantes 0.14.0 sont des archives historiques distinctes ; elles ne sont pas toutes des sources de référence équivalentes. L'absence de l'archive 0.18.0 reste un manque de source.

## Problèmes importants constatés

1. **Accès Android 10** : la branche API 29 de 0.16.1 renvoyait `false` dans `hasStorageReadAccess()` ; les actions de demande de permission indiquaient que le SAF devait être utilisé, mais le navigateur SAF n'existait pas encore dans cette version.
2. **SAF parallèle** : depuis 0.17.0, le navigateur `DocumentFile` a été ajouté à côté du navigateur local `File`. Les préférences, aperçus et opérations ne sont pas parfaitement identiques entre les deux moteurs.
3. **Régression esthétique** : 0.15.0 puis 0.19.0 ont adopté un thème quasi monochrome alors que 0.14.0 utilisait déjà des accents orange, turquoise et violet et des couleurs par type de fichier.
4. **Multi-sélection et renommage** : 0.19.0 contient les cases de sélection et une prévalidation des opérations, conservées en 1.0.0. Les contrôles sont visibles dans les deux vues.
5. **Confusion des chemins** : `/` est la racine Linux du système et non la racine utilisateur. Le point d'entrée reste le volume partagé principal, pas une fausse promesse d'accès root.

## Décisions 1.0.0

- Réintroduire un thème coloré, sombre mais non monochrome : ambre, turquoise, violet, bleu, vert et rouge, avec couleurs d'accent par type de fichier.
- Garder le chemin et les actions de recherche directement dans l'en-tête.
- Utiliser d'abord le navigateur `File` lorsque l'accès général est autorisé.
- Garder le SAF comme mode de repli/choix manuel, sans le forcer sur Android 11+.
- Ajouter deux variantes de distribution pour gérer la contrainte Android 10 : `play` (target API 36) et `android10Legacy` (target API 29 + `requestLegacyExternalStorage=true`, distribution directe uniquement).
- Conserver les fonctions travaillées depuis les versions précédentes dans le moteur local et reprendre les fonctions SAF disponibles sans prétendre à une parité totale lorsque le fournisseur Android limite ses opérations.

## Limites à connaître

Le SAF est un mécanisme URI/document, pas une autorisation générale au système de fichiers. Il ne peut pas donner un accès arbitraire à tout le volume, aux données privées des applications ou aux zones interdites par Android. `MANAGE_EXTERNAL_STORAGE` n'existe qu'à partir d'Android 11. Pour Android 10, le mode `requestLegacyExternalStorage` nécessite une cible API 29 ; il n'est pas compatible avec la variante moderne Play ciblant API 36. La variante Android 10 legacy est donc réservée à la distribution directe.
