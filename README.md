# AmethyysNPCs

Plugin Paper 1.21.11 / Java 21. BetterModel 2.2.0 est une dépendance optionnelle à l'exécution mais nécessaire pour afficher les modèles BetterModel. MythicMobs, ItemsAdder et Multiverse sont détectés comme intégrations optionnelles.

## Compilation
`mvn clean package` — le JAR ombré est produit dans `target/AmethyysNPCs-0.1.0.jar`.

## Installation
1. Installer Paper 1.21.11 et Java 21.
2. Installer BetterModel 2.2.0.
3. Placer le JAR dans `plugins/` et démarrer le serveur.
4. Définir les NPC dans `plugins/AmethyysNPCs/npc.yml` ou utiliser `/anpc create`.

## Exemple npc.yml
```yaml
npcs:
  gardien:
    display-name: "&6Gardien"
    type: "limb:steve" # ou un nom de modèle BetterModel
    world: [world]
    x: 0.5
    y: 65
    z: 0.5
    yaw: 0
    pitch: 0
    skin: "00000000-0000-0000-0000-000000000000" # UUID de profil BetterModel, si souhaité
```

`type: player` sélectionne le limb `steve`; `skin` peut être un UUID ou le nom d'un joueur actuellement connecté. Pour les modèles classiques, `type` est le nom BetterModel. `world` accepte un nom, une liste de noms ou `all`.

Scripts : `plugins/AmethyysNPCs/scripts/<id>.aa`. Les événements et blocs utilisent une indentation de 4 espaces.

```python
on right click:
    say local "Bonjour !"
    play sound "entity.villager.yes"

on entity approach:
    say local "Bienvenue près de moi !"

on interval:
    if flag (ouvert) is on:
        say local "La porte est ouverte."
```

## Commandes disponibles
- `/anpc list`
- `/anpc create <id> <modele|limb:nom>`
- `/anpc tp <id>`
- `/anpc center <id>`
- `/anpc rename <id> <nom>`
- `/anpc kill <id>`
- `/anpc reload`

## État de cette livraison (0.1.0)
Cette archive fournit le socle exécutable : chargement `npc.yml`, NPC BetterModel basés sur `DummyTracker`, hitbox d'interaction, suivi des joueurs visibles, déplacement interpolé, animations, commandes de base, événements de clic/approche/intervalle, flags, premières conditions/actions, et stockage SQLite.

Ce n'est pas encore l'intégralité de la liste « validée » de la documentation : le langage est en première tranche (les fonctions, boucles, tous les types d'événements, la totalité des variables/conditions/actions et les modes avancés de déplacement ne sont pas encore implémentés). MythicMobs, ItemsAdder et Multiverse sont déclarés en dépendances souples mais leurs adaptateurs ne sont pas encore codés. La gestion des skins personnalisés par fichier et la vérification en jeu restent à terminer. La compilation n'a pas pu être lancée dans cet environnement faute de Maven et d'accès réseau ; le workflow GitHub Actions fourni effectuera le build.
