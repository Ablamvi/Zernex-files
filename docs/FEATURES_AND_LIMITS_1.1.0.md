# Matrice des fonctionnalités — ZERNEX Files 1.1.0

Cette matrice sépare les fonctions effectivement codées des objectifs futurs. Elle prévaut sur toute présentation marketing.

## Gestionnaire local

| Domaine | Fonction | État 1.1.0 |
|---|---|---|
| Navigation | Chemin, fil d’Ariane, dossier parent, recherche et recherche avancée | Intégré |
| Affichage | Liste/grille, tri, cachés, catégories visuelles | Intégré |
| Organisation | Favoris, récents, dossier de départ, propriétés | Intégré |
| Sélection | Multi-sélection, tout sélectionner, renommage groupé | Intégré |
| Transferts | Copier/couper/coller, gestion de collisions, partage | Intégré |
| Suppression | Corbeille, restauration, suppression confirmée | Intégré |
| Outils | Éditeur texte, aperçu image/PDF/audio/vidéo, MD5, doublons | Intégré |
| Sécurité fichiers | Garde-fous de chemins pour extraction | Intégré |

## Archives — moteur local 1.1.0

| Format | Lister | Créer | Extraire |
|---|---:|---:|---:|
| ZIP | Oui | Oui | Oui |
| ZIP AES-256 | En-têtes visibles | Oui, mot de passe ≥ 8 caractères | Oui, mot de passe |
| 7Z | Oui | Oui | Oui, mot de passe en entrée |
| TAR | Oui | Oui | Oui |
| TAR.GZ / TGZ | Oui | Oui | Oui |
| TAR.BZ2 / TBZ / TBZ2 | Oui | Oui | Oui |
| TAR.XZ / TXZ | Oui | Oui | Oui |
| GZ seul | Taille inconnue avant lecture | Non | Oui |
| BZ2 seul | Taille inconnue avant lecture | Non | Oui |
| XZ seul | Taille inconnue avant lecture | Non | Oui |
| RAR / RAR5 / ISO / CAB / ARJ / ZST | Non | Non | Non |

Chiffrement : seul le ZIP est produit chiffré AES-256. Le créateur 7Z et le créateur TAR n’ajoutent pas de mot de passe dans cette livraison. Ne pas considérer les mots de passe de l’application comme un chiffrement global.

## Compatibilité stockage

| Variante | Android | Méthode d’accès |
|---|---|---|
| play | Android 11+ | Accès direct après autorisation spéciale, SAF facultatif |
| play | Android 10 | SAF limité au dossier sélectionné |
| android10Legacy | Android 10 | Stockage partagé legacy et permissions classiques ; distribution directe uniquement |

Le moteur avancé d’archives est intégré au navigateur local. Le navigateur SAF garde actuellement ses actions ZIP ; les nouveaux formats ne sont pas présentés comme pris en charge en SAF.

## Fonctions futures — ne pas annoncer comme disponibles

1. Onglets et double panneau.
2. Files de transferts durables : progression réelle, pause, reprise, annulation et journal d’opérations.
3. Connexions SFTP, SMB et WebDAV avec secrets stockés de façon protégée.
4. Accès Shizuku facultatif avec consentement, sans contournement silencieux des contrôles Android.
5. RAR/RAR5, archives multi-volumes et édition du contenu d’une archive sans extraction complète.
6. Parité de fonctions entre stockage direct et SAF.
7. Tests physiques Android 10, Android 11+, stockage USB et carte SD.

## Conditions strictes de livraison

- Aucun élément existant n’est écrasé lors d’une extraction.
- Toute opération destructive est confirmée ou passe par la corbeille.
- Un résultat d’opération n’est annoncé comme réussi qu’après vérification de son résultat.
- Une permission Android refusée n’est jamais présentée comme un dossier vide.
- Les limites d’extraction actuelles sont 10 000 entrées, 256 Mio par fichier et 512 Mio au total.
- La CI doit exécuter le vérificateur statique, les tests unitaires, le lint et les deux compilations Release.
- Les APK signés ne sont déclarés signés que si le certificat réel est vérifié ; les secrets de signature ne sont jamais ajoutés au dépôt.
