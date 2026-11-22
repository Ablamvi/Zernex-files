# Plan de test physique — ZERNEX Files 1.0.1

## Android 10 — variante `android10Legacy`

1. Installer l'APK sur un appareil API 29 propre.
2. Ouvrir ZERNEX : la demande Android READ/WRITE doit apparaître.
3. Accorder les permissions ; le navigateur doit afficher directement le stockage partagé, sans lancer le sélecteur SAF.
4. Tester création de dossier/fichier texte, renommage, renommage multiple, copie, déplacement, suppression vers corbeille et restauration.
5. Tester recherche dans les sous-dossiers, tri, liste/grille, favoris, récents et fichiers cachés.
6. Tester création et extraction ZIP, aperçu image/PDF/audio/vidéo, éditeur texte et détails/MD5.
7. Refuser la permission au premier lancement : l'écran doit montrer un état verrouillé et une action de nouvelle demande, pas un faux dossier vide.

## Android 11 à Android 16 — variante `play`

1. Installer l'APK sur un appareil API 30+ propre.
2. Au premier lancement, ouvrir les paramètres Android propres à **Gérer tous les fichiers**.
3. Après retour et activation, afficher le volume partagé principal sans sélection de dossier.
4. Refuser l'autorisation : les opérations de fichiers doivent rester bloquées et l'état doit être clair.
5. Révoquer l'autorisation depuis les paramètres pendant que l'application est fermée, puis relancer : l'application doit détecter la révocation et ne pas présenter les erreurs comme un dossier vide.
6. Tester toutes les opérations locales citées dans la section Android 10.
7. Choisir explicitement le mode SAF depuis le menu, sélectionner un dossier, quitter/réouvrir l'application et vérifier la persistance de l'autorisation.
8. Tester les opérations SAF avec un fournisseur local et avec une carte SD si disponible. Toute action non prise en charge par le fournisseur doit afficher un échec précis et ne pas prétendre avoir réussi.

## Sélection et opérations critiques

- Sélectionner plusieurs éléments à la case, par appui long, et par « Tout sélectionner » ; vérifier les compteurs en liste et en grille.
- Sélectionner un dossier parent et un enfant puis lancer un traitement : l'enfant ne doit pas être traité une deuxième fois.
- Renommage groupé : conserver les extensions ; refuser les noms vides, séparateurs, NUL, plus de 255 octets et collisions ; vérifier qu'aucun fichier existant n'est écrasé.
- Couper/coller : interdire un second lot tant que le premier n'est pas collé/effacé ; interdire le déplacement d'un dossier vers lui-même ou un descendant.
- Annuler une copie volumineuse : vérifier le nettoyage des éléments partiels.
- Tester plusieurs fichiers de même nom en destination : un suffixe ` (copie 1)` doit être ajouté sans écrasement.
- Quitter l'écran ou appuyer sur Retour pendant une opération : l'interface doit prévenir ou bloquer les actions incompatibles.

## Validation finale

La release n'est prête à publier qu'après compilation CI sans erreur, installation des deux APK et réussite de tous les tests applicables sur appareils réels. Les tests de ce document ne sont pas déclarés réussis dans la livraison source tant qu'ils n'ont pas été exécutés sur appareils.
