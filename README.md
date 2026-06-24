# MapMinecraft

A NeoForge mod for Minecraft 1.21.1 that adds one block: the Live Map Projector. Place it and it scans the terrain around it, then projects a floating 3D voxel hologram of the world above itself. The hologram uses real block textures and biome colors, with animated translucent water and full 3D trees.

Read this in: English (below) or [Français](#français).

---

## Features

- A 3D voxel hologram of the terrain, not a flat minimap. The projector reads a vertical slice up to 256 blocks tall and rebuilds it as a textured voxel diorama above the block.
- Real block textures and biome colors. Each face uses the block's actual texture, tinted per biome. Water is animated and translucent.
- An in-game settings GUI (right-click the block) for scan radius (8 to 128 blocks), scan interval, hologram size, hover height, float bob, and rotation speed.
- Center on coordinates: project a remote area instead of the projector's own location.
- Hologram on/off: keep the projector as a decorative scanner without the floating map.
- A redstone entity radar. The block emits a signal from 0 to 15 based on how many entities are nearby, with a comparator output and detection modes: All, Players, Hostiles, Animals, Items.
- English and French translations.

## Installation

1. Install [NeoForge 21.1.234 or newer](https://neoforged.net/) for Minecraft 1.21.1.
2. Download the latest `mapminecraft-x.y.z.jar` from the [Releases](../../releases) page.
3. Put the `.jar` in your `mods/` folder.
4. Launch the game.

Requirements: Minecraft 1.21.1, NeoForge 21.1.234+, Java 21.

## Crafting

Shaped recipe (4 Paper, 4 Redstone, 1 Compass):

```
P R P     P = Paper
R C R     R = Redstone Dust
P R P     C = Compass
```

## Usage

1. Place the Live Map Projector on the ground.
2. It starts scanning right away, and a hologram of the surrounding terrain appears above it.
3. Right-click the block to open the settings GUI and adjust radius, size, rotation, detection mode, and so on.
4. Wire the block to redstone to use it as an entity radar. The signal scales with the number of detected entities.

Note: the projector only scans loaded chunks. A very large radius, or a "center on coords" target that reaches into unloaded chunks, will read those areas as empty. The practical limit is roughly your server's view distance.

## Building from source

```bash
git clone https://github.com/LKDM7/MapMinecraft.git
cd MapMinecraft
./gradlew build      # Windows: gradlew.bat build
```

The mod jar is written to `build/libs/mapminecraft-x.y.z.jar`.

To run a dev client or server:

```bash
./gradlew runClient
./gradlew runServer
```

## License

Apache License 2.0. See [LICENSE](LICENSE).

---

## Français

Un mod NeoForge pour Minecraft 1.21.1 qui ajoute un seul bloc : le Projecteur de carte vivante. Posez-le et il scanne le terrain autour de lui, puis projette un hologramme voxel 3D flottant du monde au-dessus de lui. L'hologramme utilise les vraies textures de blocs et les couleurs de biome, avec de l'eau animée et translucide et des arbres en 3D.

### Fonctionnalités

- Un hologramme voxel 3D du terrain, pas une mini-carte plate. Le projecteur lit une tranche verticale jusqu'à 256 blocs de haut et la reconstruit en diorama voxel texturé au-dessus du bloc.
- Les vraies textures de blocs et couleurs de biome. Chaque face utilise la texture réelle du bloc, teintée selon le biome. L'eau est animée et translucide.
- Une interface de réglages en jeu (clic droit sur le bloc) pour le rayon de scan (8 à 128 blocs), l'intervalle de scan, la taille de l'hologramme, la hauteur de flottement, l'oscillation et la vitesse de rotation.
- Centrer sur des coordonnées : projeter une zone distante au lieu de l'emplacement du projecteur.
- Hologramme activé/désactivé : garder le projecteur comme scanner décoratif sans la carte flottante.
- Un radar d'entités en redstone. Le bloc émet un signal de 0 à 15 selon le nombre d'entités à proximité, avec une sortie de comparateur et des modes de détection : Tout, Joueurs, Hostiles, Animaux, Objets.
- Traductions en anglais et en français.

### Installation

1. Installez [NeoForge 21.1.234 ou plus récent](https://neoforged.net/) pour Minecraft 1.21.1.
2. Téléchargez le dernier `mapminecraft-x.y.z.jar` depuis la page [Releases](../../releases).
3. Placez le `.jar` dans votre dossier `mods/`.
4. Lancez le jeu.

Prérequis : Minecraft 1.21.1, NeoForge 21.1.234+, Java 21.

### Fabrication

Recette façonnée (4 Papier, 4 Redstone, 1 Boussole) :

```
P R P     P = Papier
R C R     R = Poudre de redstone
P R P     C = Boussole
```

### Utilisation

1. Posez le Projecteur de carte vivante sur le sol.
2. Il commence à scanner tout de suite, et un hologramme du terrain environnant apparaît au-dessus.
3. Clic droit sur le bloc pour ouvrir l'interface de réglages et ajuster le rayon, la taille, la rotation, le mode de détection, etc.
4. Reliez le bloc à de la redstone pour l'utiliser comme radar d'entités. Le signal varie selon le nombre d'entités détectées.

Note : le projecteur ne scanne que les chunks chargés. Un très grand rayon, ou une cible « centrer sur coords » atteignant des chunks non chargés, lira ces zones comme vides. La limite pratique correspond environ à la distance de rendu de votre serveur.

### Compilation depuis les sources

```bash
git clone https://github.com/LKDM7/MapMinecraft.git
cd MapMinecraft
./gradlew build      # Windows : gradlew.bat build
```

Le jar du mod est écrit dans `build/libs/mapminecraft-x.y.z.jar`.

Pour lancer un client ou serveur de développement :

```bash
./gradlew runClient
./gradlew runServer
```

### Licence

Licence Apache 2.0. Voir [LICENSE](LICENSE).
