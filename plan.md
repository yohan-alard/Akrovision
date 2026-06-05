# Plan de développement — AkroVision

## Vision du projet

Application Android compagnon pour le jeu de société **Acropolis**.
Les joueurs empilent des cartons colorés pour former une ville. En fin de partie,
l'application utilise la caméra pour détecter les couleurs visibles (tuiles du dessus)
et calcule automatiquement le score de chaque joueur via un overlay AR.

**Stack :** Kotlin + Jetpack Compose + CameraX + ML Kit
**V1 :** 100% offline
**V2 :** Scores en ligne, tutoriels, tips/donations

---

## Règles du jeu (contexte métier)

Voir `regles.md` pour le détail complet.

### Districts (5 couleurs)

- Habitants - Marchés - Casernes - Jardins - Temples

### Formule de score (par couleur)

```
Score couleur = valeur du plus grand groupe connecté × total des lauriers de cette couleur
```

1. **Groupes connectés** : parmi les tuiles visibles (sommet de chaque pile), trouver les groupes adjacents de même couleur. Seul le **plus grand groupe** compte.
2. **Valeur du groupe** : somme des niveaux de chaque tuile du groupe (niveau 1 = 1 pt, niveau 2 = 2 pts, niveau 3 = 3 pts)
3. **Lauriers** : total des lauriers de cette couleur dans **toute la cité** (y compris les tuiles cachées sous d'autres)
4. **Score total** = somme des scores des 5 couleurs + bonus objectifs

### Point clé pour la détection

Les lauriers sont sur **toutes** les tuiles (pas seulement celles visibles).
La caméra ne peut pas les détecter → ils sont saisis manuellement par le joueur.

### Modes de jeu

- **Mode normal** : formule de base ci-dessus
- **Mode avancé** : objectifs de cité (ex : 10 habitations → +10 pts ou ×2 score) + bonus variantes

---

## Fonctionnalités principales

### Menu principal

1. **Calcul rapide** — saisie manuelle des tuiles visibles → score instantané
2. **Partie complète** — suivi du score de chaque joueur tout au long d'une partie

### Détection AR (coeur de l'app)

- Caméra pointée sur la cité (vue de dessus)
- Détection automatique des **couleurs visibles** via ML Kit
- Saisie assistée des **hauteurs** (tap sur chaque pile → 1/2/3) et des **lauriers**
- Overlay AR : grille colorée + niveaux + score calculé en temps réel

---

## Ordre de priorité

> **Principe :** La valeur centrale de l'app est la détection AR. On valide ça en premier.
> Si la caméra ne détecte pas les couleurs correctement, tout le reste est inutile.

```
Phase 0 → Phase 1 (POC caméra) → Phase 2 (algorithme score)
→ Phase 3 (overlay AR) → Phase 4 (UI complète) → Phase 5 (publication)
```

**Go / No-go après Phase 1 :** si la précision est insuffisante, revoir l'approche
(marqueurs visuels, QR codes, saisie assistée) avant de continuer.

---

## Phase 0 — Restructuration du projet (1–2 jours)

Le projet actuel est un squelette Maven Java. Il faut le convertir en projet Android.

**Tâches :**

- [ ] Créer un nouveau projet Android Studio (Gradle + Kotlin DSL) dans ce dossier
- [ ] Configurer `build.gradle.kts` : Compose, CameraX, ML Kit, Navigation
- [ ] Mettre en place l'architecture MVVM (ViewModel + StateFlow + Repository)
- [ ] Package racine : `fr.yaltech.games.akrovision`
- [ ] Supprimer `pom.xml` devenu inutile

**Livrables :** App Android vide qui compile et se lance

---

## Phase 1 — POC détection caméra — PRIORITÉ ABSOLUE (2–3 semaines)

> Les deux doivent fonctionner ensemble. Si l'un échoue, c'est un no go pour le projet.
> Pas d'UI soignée ici — deux écrans de test bruts, un par défi technique.

### POC 1A — Détection des couleurs

- [ ] Photographier les tuiles physiques du jeu → constituer la palette de référence (5 couleurs)
- [ ] Intégrer CameraX + ML Kit Image Analysis
- [ ] Algorithme HSV : détecter la couleur dominante par zone de l'image
- [ ] Afficher en temps réel la couleur détectée sur chaque zone (overlay debug)
- [ ] Tester en lumière naturelle, artificielle, faible, angles variables
- [ ] Critère de validation : >90% de précision sur les 5 couleurs

### POC 1B — Détection de la hauteur (3 approches à tester en parallèle)

**Approche A — ARCore plane detection** (sans capteur ToF, compatible la plupart des Android)
- [ ] Intégrer ARCore de base
- [ ] Détecter la surface de la table comme plan horizontal
- [ ] Mesurer la hauteur des piles au-dessus du plan → mapper sur 1/2/3 niveaux
- [ ] Tester la précision sur des tuiles de quelques mm d'épaisseur

**Approche B — Scan de côté + détection de contours** (sans hardware spécial)
- [ ] Intégrer OpenCV Android ou ML Kit Image Segmentation
- [ ] Vue de profil : détecter les bandes horizontales de chaque niveau de tuile
- [ ] Compter les bandes → 1, 2 ou 3 niveaux
- [ ] Tester la robustesse selon l'angle et la lumière

**Approche C — Angle oblique ~45°** (un seul scan pour couleur + hauteur)
- [ ] Depuis un angle oblique, les côtés des piles sont visibles
- [ ] Combiner détection couleur (dessus) + comptage de niveaux (côté) en une prise
- [ ] Tester la fiabilité de la segmentation à cet angle

**Critère de validation :** au moins une approche atteint >85% de précision sur 1/2/3 niveaux

### Go / No-go

| Résultat | Décision |
|----------|----------|
| Couleurs OK + Hauteur OK | Continuer vers Phase 2 |
| Couleurs OK, Hauteur KO toutes approches | No go — revoir le concept |
| Couleurs KO | No go — revoir le concept |

**Livrables :** Deux écrans de test validant couleurs ET hauteur, avec taux de précision mesuré

---

## Phase 2 — Algorithme de score (1 semaine)

Implémenter la logique de calcul branchée sur les données réelles détectées en Phase 1.

### Modèle de données

```kotlin
data class Tile(val color: DistrictColor, val level: Int, val laurels: Int)
data class CityGrid(val cells: Map<Position, List<Tile>>) // List = pile de tuiles

enum class DistrictColor { HABITANTS, MARCHES, CASERNES, JARDINS, TEMPLES }
```

### Algorithme de score

1. Extraire les tuiles visibles (sommet de chaque pile)
2. Trouver les groupes connexes par couleur (BFS sur la grille)
3. Calculer la valeur du plus grand groupe (somme des niveaux)
4. Compter les lauriers totaux par couleur (toutes tuiles, y compris cachées)
5. Score couleur = valeur groupe × lauriers

**Tâches :**

- [ ] Implémenter `CityGrid` et les modèles de données
- [ ] Algorithme BFS de recherche de groupes connexes
- [ ] Calculateur de score mode normal
- [ ] Calculateur de score mode avancé (objectifs + bonus)
- [ ] Tests unitaires sur le calculateur (cas limites, groupes non connexes, hauteurs mixtes)

**Livrables :** Module de calcul de score testé et validé

---

## Phase 3 — Overlay AR complet (1–2 semaines)

Assembler la détection (Phase 1) + le calcul (Phase 2) dans une expérience AR fluide.

### Ce que la caméra détecte vs ce que le joueur saisit

| Donnée | Méthode |
|--------|---------|
| Couleur de la tuile visible | Détection automatique ML Kit |
| Position sur la grille | Détection automatique ML Kit |
| Hauteur de la pile (1/2/3) | Tap du joueur sur la tuile |
| Lauriers totaux par couleur | Saisie du joueur en fin de partie |

**Tâches :**

- [ ] Overlay AR : grille dessinée sur le flux caméra, zones colorées selon détection
- [ ] Interface tap → sélectionner le niveau (1/2/3) sur chaque zone détectée
- [ ] Interface de saisie des lauriers par couleur
- [ ] Connexion détection → calculateur → score affiché en overlay en temps réel
- [ ] Bouton "Valider ce score" pour enregistrer dans la partie
- [ ] Correction manuelle possible case par case si détection incorrecte
- [ ] (Optionnel) Scan de côté pour détecter la hauteur automatiquement

**Livrables :** Écran AR complet, du scan au score final

---

## Phase 4 — UI complète & expérience de jeu (1–2 semaines)

**Tâches :**

- [ ] Menu principal (Calcul rapide / Partie complète)
- [ ] Écran Nouvelle partie : saisie des joueurs, choix du mode de jeu
- [ ] Écran Suivi de partie : score de chaque joueur, round par round
- [ ] Écran Fin de partie : classement final, détail des scores par couleur
- [ ] Écran Calcul rapide : saisie manuelle directe sans caméra
- [ ] Navigation Compose entre tous les écrans
- [ ] Animations de score (compteur animé, podium en fin de partie)
- [ ] Thème visuel inspiré du jeu (antiquité, pierres, couleurs des districts)
- [ ] Historique des parties (stockage local Room)
- [ ] Mode sombre

**Livrables :** Application complète et fluide

---

## Phase 5 — Tests & publication (1 semaine)

**Tâches :**

- [ ] Tests de la détection couleur dans différentes conditions lumineuses
- [ ] Tests sur plusieurs appareils physiques (tailles d'écran, versions Android)
- [ ] Vérification des deux modes de jeu (normal et avancé)
- [ ] Génération du `.aab` signé
- [ ] Création du compte Google Play Developer (25$ unique)
- [ ] Fiche Play Store : description, captures d'écran, icône
- [ ] Publication en beta fermée avant sortie publique

**Livrables :** App publiée sur le Play Store

---

## Phase 6 — V2 (après la sortie)

| Fonctionnalité | Technologie |
|----------------|-------------|
| Sauvegarde des scores en ligne | Firebase Firestore |
| Authentification joueurs | Firebase Auth |
| Tutoriels vidéo / règles intégrées | Vidéos locales ou YouTube |
| Système de tips/donations | Google Play Billing Library (In-App) |
| Détection automatique de la hauteur | ARCore Depth API (appareils ToF) |

---

## Questions ouvertes

- [x] Quelles sont les règles de score ? → Voir `regles.md`
- [x] Y a-t-il un mode avancé ? → Oui, objectifs de cité + bonus variantes
- [ ] Quelles sont exactement les couleurs des tuiles dans le jeu physique ? (photos à prendre pour calibrer ML Kit en Phase 1)
- [ ] Combien de lauriers par tuile, par couleur ? (à documenter pour la saisie assistée)
- [ ] Quel est le nombre maximum de joueurs prévu ?
- [ ] Les objectifs du mode avancé sont-ils fixes ou variables d'une partie à l'autre ?
