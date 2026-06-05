---
name: project-akrovision
description: Contexte du projet AkroVision — application Android compagnon pour le jeu Acropolis
metadata:
  type: project
---

AkroVision est une application Android compagnon pour le jeu de société **Acropolis**.

**Règles du jeu :** Les joueurs empilent des tuiles colorées (petits cartons). Seules les tuiles visibles depuis le dessus comptent en fin de partie. Le score dépend de la couleur et du placement de chaque tuile dans la cité. Il existe un mode normal et un mode avancé avec des règles supplémentaires.

**Fonctionnalités clés :**
- Menu principal avec deux modes : Calcul rapide (saisie manuelle) et Partie complète (suivi par joueur)
- Détection AR via caméra (vue de dessus) : ML Kit détecte les couleurs visibles → calcul de score automatique avec overlay AR
- 100% offline en V1

**V2 prévue :** Firebase (scores en ligne), tutoriels, système de tips/donations (Google Play Billing, app gratuite)

**Stack :** Kotlin + Jetpack Compose + CameraX + ML Kit  
**Package :** `fr.yaltech.games.akrovision`

**Why:** L'utilisateur est débutant Android, donc les choix techniques privilégient les APIs officielles Google (ML Kit plutôt qu'ARCore complet).

**How to apply:** Toujours adapter les explications au niveau débutant. Favoriser les solutions simples et bien documentées. La Phase 1 (logique de jeu sans AR) est prioritaire pour valider les règles de score avant d'implémenter la caméra.