# ZERNEX Files 2.0.0 — matrice des fonctions

Légende : **Fonctionnel** = présent dans le code et vérifié par les tests automatisés disponibles ; **Partiel** = limité par le mode de stockage ou le fournisseur Android ; **Non inclus** = pas implémenté dans cette version. Les tests automatisés ne remplacent pas les essais physiques.

| Domaine | Fonction | État | Limite connue |
|---|---|---|---|
| Interface | Palette monochrome + rouge/vert/jaune | Fonctionnel | Aperçus photo conservent leurs couleurs propres |
| Interface | Icônes sémantiques, catégories visuelles, navigation inférieure | Fonctionnel | Les libellés d'accessibilité restent nécessaires |
| Navigation | Liste/grille, fil d'Ariane, tri, recherche par nom | Fonctionnel | Recherche récursive plafonnée |
| Stockage | Navigateur local | Partiel | Dépend des permissions Android |
| Stockage | SAF et dossier choisi | Partiel | Capacités dépendantes du fournisseur |
| Fichiers | Copier, couper, coller, renommer, sélection multiple | Fonctionnel | Certaines opérations SAF peuvent être refusées par le fournisseur |
| Fichiers | Corbeille et restauration | Partiel | Restauration dépend de la disponibilité de la destination |
| Fichiers | Favoris, récents, propriétés, MD5, doublons | Fonctionnel | Recherche de doublons plafonnée |
| Aperçus | Images, PDF, audio, vidéo | Partiel | Certains formats nécessitent une application externe |
| Édition | Éditeur texte | Fonctionnel | Limite de taille / encodage UTF-8 |
| Archives locales | Lire, créer, extraire ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ | Fonctionnel | RAR/RAR5 non pris en charge |
| Compression | GZ, BZ2, XZ | Fonctionnel | Décompression simple |
| Chiffrement archive | ZIP AES-256 | Fonctionnel | Chiffrement disponible uniquement pour ZIP |
| Archives SAF | Parcourir et extraire ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, GZ, BZ2 et XZ ; créer ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2 et TAR.XZ | Fonctionnel | Le fournisseur SAF doit autoriser la lecture et l’écriture ; extraction copiée via cache temporaire borné |
| Réseau | SFTP, SMB, FTP, WebDAV | Non inclus | À développer |
| Multi-fenêtres | Onglets et double panneau | Non inclus | À développer |
| Opérations | File persistante, pause/reprise en arrière-plan | Non inclus | À développer |
| Système | Shizuku et accès root | Non inclus | À développer |
| Sécurité | Coffre chiffré biométrique | Non inclus | À développer |
| Développement | Éditeur hexadécimal et outils APK avancés | Non inclus | À développer |

## Critères de livraison 2.0.0

- [ ] Vérificateur statique réussi.
- [ ] Tests JVM réussis.
- [ ] Lint Play réussi.
- [ ] APK Play compilé.
- [ ] APK Android 10 Legacy compilé.
- [ ] Signature et certificat vérifiés.
- [ ] Installation et navigation testées sur Android 10.
- [ ] Installation et permissions testées sur Android 11+.
- [ ] Test du sélecteur SAF et d'un stockage amovible.
- [ ] Test manuel des actions de copie, déplacement, suppression, restauration et archives.

Une case non cochée ne doit pas être présentée comme une validation accomplie.
