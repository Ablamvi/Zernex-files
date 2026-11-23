# ZERNEX Files 2.0 — système de design

## Palette non négociable

| Token | Valeur | Rôle |
|---|---|---|
| Background | #0B0B0C | Fond principal |
| Surface | #151516 | Panneaux |
| Surface variant | #222224 | Cartes et champs |
| Texte | #F5F5F5 | Texte principal |
| Texte secondaire | #B8B8BA | Métadonnées |
| Rouge | #E34D4D | Médias, suppression, erreurs |
| Vert | #38C978 | Documents, validation, succès |
| Jaune | #F4C542 | Archives, applications, action primaire |

Aucune autre couleur chromatique n'est autorisée dans les écrans de navigation, icônes, cartes, menus ou aperçus. Les aperçus photo/vidéo conservent naturellement les pixels du média lui-même.

## Règles d'usage

1. Surfaces plates et monochromes ; pas de gradient, glow, ombre colorée ou bordure arc-en-ciel.
2. Les icônes sont la première information visuelle. Le nom vient ensuite ; taille et date restent secondaires.
3. Dossiers neutres. Catégories :
   - médias (image, vidéo, audio) : rouge ;
   - documents, tableaux, présentations et code : vert ;
   - archives et packages d'applications : jaune ;
   - type inconnu : gris.
4. Un accent ne décore jamais une ligne entière. Il est limité à l'icône, un petit repère et l'état sélectionné.
5. Les actions groupées apparaissent uniquement lors d'une sélection.
6. Les écrans local et SAF utilisent la même classification.
7. Les commandes évidentes sont identifiées par icône ; une description accessible reste obligatoire.

## Vérification

Le script `tools/verify_source.py` contrôle les tokens, les catégories et l'absence des anciennes couleurs bleue, violette, cyan et orange. Les tests JVM contrôlent les catégories. Le lint et les builds Android restent obligatoires.
