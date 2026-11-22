> Document historique de développement. Pour les règles de livraison actuelles, voir `../README.md`, `AUDIT_HISTORIQUE_0.14-0.19.md` et `TEST_PLAN_1.0.1.md`.

# Règles des opérations — ZERNEX Files 0.19.0

Ce document décrit les garde-fous ajoutés dans les navigateurs `File` et SAF.

## Sélection

- La sélection est accessible par une case à cocher visible en liste et en grille ; l'appui long reste une solution secondaire.
- « Tout sélectionner » ne cible que les éléments actuellement affichés (après recherche et filtres).
- Changer de dossier, de recherche ou de filtre efface la sélection afin de ne pas appliquer une action à des éléments qui ne sont plus visibles.
- Lorsqu'un dossier et l'un de ses descendants sont tous deux sélectionnés, seul le dossier parent est traité.
- Le bouton Annuler et le bouton Retour Android quittent le mode sélection sans modifier les fichiers.

## Renommage

- Un élément : ouvre le renommage simple.
- Plusieurs éléments : ouvre le renommage par préfixe/suffixe ; les extensions des fichiers sont conservées.
- Les noms vides, `.` / `..`, les séparateurs de chemin, le caractère NUL et les noms de plus de 255 octets UTF-8 sont refusés.
- Les conflits sont contrôlés avant le traitement. Le renommage local bloque les renommages croisés et tente de restaurer les changements déjà effectués si une opération échoue.
- En SAF, chaque fournisseur Android peut refuser un renommage ; le résultat indique alors le nombre de succès et d'échecs. Le SAF ne garantit pas une transaction atomique.

## Presse-papiers copier/couper

- Un seul lot de presse-papiers est actif à la fois. Il doit être collé ou effacé avant de créer un autre lot.
- Tant que le presse-papiers contient des sources, les actions qui pourraient les invalider (renommer, mettre à la corbeille, recompresser, remplacer le lot) sont désactivées.
- La navigation vers la destination et le collage restent possibles.
- Un dossier ne peut pas être copié ou déplacé dans lui-même ni dans l'un de ses descendants.
- Les conflits de noms créent une copie suffixée au lieu d'écraser un fichier existant.
- Le déplacement est effectué par copie, puis suppression de la source. Si la suppression échoue, la copie est conservée et un avertissement indique que la source est toujours présente.
- Les limites de sécurité sont de 64 niveaux et 50 000 éléments ; la copie SAF est également limitée à 8 Gio par opération. Le navigateur local possède la même limite de 8 Gio.
- Le collage local affiche une progression et propose l'interruption ; une interruption nettoie la copie partielle en cours. Les autres tâches longues désactivent les contrôles et affichent un indicateur indéterminé.

## Suppression / corbeille

- La suppression depuis les listes demande une confirmation.
- Les racines et emplacements système protégés ne sont pas supprimables dans le navigateur local.
- La corbeille SAF copie d'abord l'élément et ne supprime la source qu'après une copie réussie ; si la suppression échoue, le message le signale.

## État occupé

- Les créations et renommages SAF, ainsi que les renommages groupés, sont exécutés hors du thread d'interface.

- Les sélections, actions, contrôles de tri, changements d'affichage, navigation par fil d'Ariane, menus et retour système sont neutralisés pendant une opération qui écrit ou déplace des fichiers.
- Les actions non pertinentes sont désactivées plutôt que laissées cliquables pour échouer ensuite.

## Vérifications réalisées pour cette version

- Tests Kotlin autonomes des fonctions pures locales : renommage multiple, prévalidation des collisions, réduction de sélection parent/enfant, copie avec nom unique et blocage du déplacement dans un descendant.
- Analyseur Kotlin lancé sur les trois sources principales : aucun diagnostic de syntaxe ; le contrôle de types complet est impossible sans les dépendances Android/Compose.
- Les 5 ressources XML de `app/src/main` sont valides.
- Le projet n'a pas été compilé ni exécuté sur appareil dans cet environnement. La compilation Gradle et les tests Android restent obligatoires avant publication.

## Tests manuels à effectuer avant publication

1. Android 10 : choisir un dossier SAF, relancer l'application et confirmer que l'accès persiste.
2. Android 10 et Android 11+ en SAF : sélectionner 2 fichiers par cases, renommer en lot, vérifier les extensions et un conflit de nom.
3. Copier puis coller dans un autre dossier ; recommencer en couper/déplacer ; vérifier qu'un déplacement dans un sous-dossier est refusé.
4. Simuler un refus d'écriture / retirer l'autorisation SAF et vérifier que l'application affiche une erreur et non un dossier vide trompeur.
5. Tester liste/grille, sélection totale, annulation par Retour, recherche dans les sous-dossiers et dossiers contenant des milliers d'éléments.
6. Tester archive, extraction, corbeille, restauration et fichiers volumineux sur un appareil réel.
7. Android 11+ : tester accès direct avec et sans `MANAGE_EXTERNAL_STORAGE`, puis le mode SAF limité à un dossier.
