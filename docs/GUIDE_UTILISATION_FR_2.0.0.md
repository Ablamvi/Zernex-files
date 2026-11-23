# Guide d'utilisation — ZERNEX Files 2.0.0

## 1. Première ouverture

1. Installe la variante adaptée à ton Android.
2. Sur Android 11 ou plus récent, autorise « Gérer tous les fichiers » si tu veux naviguer dans le stockage partagé. Tu peux aussi choisir un dossier précis avec SAF.
3. Sur Android 10, la variante Play utilise le sélecteur de dossier Android ; la variante Android 10 Legacy est prévue pour la distribution directe.
4. Les dossiers privés des autres applications restent protégés par Android.

## 2. Lire l'écran principal

- L'en-tête affiche ZERNEX Files et le chemin courant.
- Le rail visuel permet de filtrer Tous, Photos, Vidéos, Audio, Docs ou Archives.
- Les icônes indiquent la nature du fichier ; les métadonnées restent secondaires.
- Le bouton de recherche ouvre la recherche par nom.
- Les commandes de tri et d'affichage basculent entre liste et grille.
- Un appui ouvre un dossier ou le menu d'un fichier ; un appui long commence une sélection.

## 3. Actions de fichiers

Après une sélection, la barre d'actions propose selon le contexte :
- Copier ou couper, puis ouvrir la destination et coller.
- Renommer un élément ou renommer un lot.
- Mettre à la corbeille, partager, consulter les propriétés.
- Créer une archive dans le navigateur local.

Les collisions de noms sont contrôlées ; la copie crée un nom unique au lieu d'écraser silencieusement un fichier existant.

## 4. Archives dans le navigateur local

1. Sélectionne un ou plusieurs fichiers/dossiers.
2. Appuie sur Compresser.
3. Choisis ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2 ou TAR.XZ.
4. Le mot de passe AES-256 facultatif est disponible pour ZIP uniquement.
5. Ouvre une archive prise en charge pour afficher ses entrées et choisir l'extraction.

L'extraction refuse les chemins absolus, traversées, liens et collisions, et applique des limites de taille et de nombre d'entrées.

## 5. SAF et stockage amovible

SAF est le mode Android qui donne accès au dossier que tu sélectionnes. Le fournisseur Android peut limiter certaines opérations. Dans la version 2.0.0, le mode SAF propose les actions de fichiers et ZIP déjà présentes ; le moteur d'archives multi-format avancé est intégré au navigateur local. Cette différence est affichée dans la matrice des fonctions.

## 6. Outils et navigation

La barre inférieure ouvre Fichiers, Récents, Favoris et Outils en mode local. La page Outils regroupe Stockage, Corbeille, Doublons, Accès, Paramètres et choix SAF. En mode SAF, les raccourcis inférieurs sont Fichiers, Favoris, Corbeille et Réglages.

## 7. Palette et icônes

L'interface utilise noir, graphite, gris et blanc, avec trois accents :
- Rouge : médias et actions destructives.
- Vert : documents et réussite.
- Jaune : archives, applications et action principale.

Aucun dégradé multicolore n'est utilisé dans les icônes de fichiers ou les surfaces de navigation.
