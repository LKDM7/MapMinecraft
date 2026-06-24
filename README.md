# MapMinecraft — Live Map Projector

A NeoForge mod for **Minecraft 1.21.1** that adds a single, spectacular block: the **Live Map Projector**. Place it down and it scans the terrain around it, then projects a floating, glowing **3D voxel hologram** of the world above itself — a real-time holographic diorama with real block textures, biome colors, translucent water and full 3D trees.

> 🇫🇷 **Version française plus bas** → [Aller à la version française](#-mapminecraft--projecteur-de-carte-vivante)

---

## ✨ Features

- **True 3D voxel hologram** — not a flat minimap. The projector reads a full volumetric slice of the world (up to 256 blocks tall) and rebuilds it as a textured voxel diorama floating above the block.
- **Real textures & biome colors** — grass tops, log rings, leaves, stone… each face uses the actual block texture, tinted per biome. Water is rendered as animated, translucent liquid.
- **Fully configurable** — a clean in-game GUI (right-click the block) lets you tune:
  - Scan radius (8–128 blocks)
  - Scan interval (refresh rate)
  - Hologram size, hover height, float "bob"
  - Rotation on/off + rotation speed
  - **Center on coordinates** — project a remote area instead of the projector's own location
  - **Hologram on/off** — keep the projector as a decorative scanner without the floating map
- **Redstone entity radar** — the projector emits a redstone signal (0–15) proportional to the number of nearby entities, with a comparator output and selectable detection modes: All / Players / Hostiles / Animals / Items.
- **Bilingual** — full English and French translations.

## 📦 Installation

1. Install [NeoForge **21.1.234** or newer](https://neoforged.net/) for Minecraft **1.21.1**.
2. Download the latest `mapminecraft-x.y.z.jar` from the [**Releases**](../../releases) page.
3. Drop the `.jar` into your `mods/` folder.
4. Launch the game.

**Requirements:** Minecraft 1.21.1 · NeoForge 21.1.234+ · Java 21

## 🛠️ Crafting

Shaped recipe (4× Paper, 4× Redstone, 1× Compass):

```
P R P     P = Paper
R C R     R = Redstone Dust
P R P     C = Compass
```

## 🎮 Usage

1. **Place** the Live Map Projector on the ground.
2. It immediately starts scanning and a hologram of the surrounding terrain appears above it.
3. **Right-click** the block to open the settings GUI and adjust radius, size, rotation, detection mode, etc.
4. Wire the block to **redstone** to use it as an entity radar (the signal scales with the number of detected entities).

> **Note:** the projector only scans loaded chunks. A very large radius or a remote "center on coords" target that reaches into unloaded chunks will read those areas as empty. Practical ceiling is roughly your server's view-distance.

## 🔨 Building from source

```bash
git clone https://github.com/LKDM7/MapMinecraft.git
cd MapMinecraft
./gradlew build      # Windows: gradlew.bat build
```

The built mod jar lands in `build/libs/mapminecraft-x.y.z.jar`.

To run a dev client/server:

```bash
./gradlew runClient
./gradlew runServer
```

## 📄 License

Licensed under the [Apache License 2.0](LICENSE).

---
---

# 🇫🇷 MapMinecraft — Projecteur de carte vivante

Un mod NeoForge pour **Minecraft 1.21.1** qui ajoute un bloc unique et spectaculaire : le **Projecteur de carte vivante**. Posez-le et il scanne le terrain autour de lui, puis projette un **hologramme voxel 3D** flottant et lumineux du monde au-dessus de lui — un diorama holographique en temps réel avec les vraies textures de blocs, les couleurs de biome, de l'eau translucide et des arbres en 3D complète.

## ✨ Fonctionnalités

- **Véritable hologramme voxel 3D** — pas une mini-carte plate. Le projecteur lit une tranche volumétrique complète du monde (jusqu'à 256 blocs de haut) et la reconstruit en diorama voxel texturé flottant au-dessus du bloc.
- **Vraies textures et couleurs de biome** — dessus d'herbe, anneaux des troncs, feuilles, pierre… chaque face utilise la vraie texture du bloc, teintée selon le biome. L'eau est rendue comme un liquide animé et translucide.
- **Entièrement configurable** — une interface en jeu (clic droit sur le bloc) permet de régler :
  - Rayon de scan (8–128 blocs)
  - Intervalle de scan (fréquence de rafraîchissement)
  - Taille de l'hologramme, hauteur de flottement, oscillation
  - Rotation activée/désactivée + vitesse de rotation
  - **Centrer sur des coordonnées** — projeter une zone distante au lieu de l'emplacement du projecteur
  - **Hologramme activé/désactivé** — garder le projecteur comme scanner décoratif sans la carte flottante
- **Radar d'entités en redstone** — le projecteur émet un signal de redstone (0–15) proportionnel au nombre d'entités à proximité, avec une sortie de comparateur et des modes de détection au choix : Tout / Joueurs / Hostiles / Animaux / Objets.
- **Bilingue** — traductions complètes en anglais et en français.

## 📦 Installation

1. Installez [NeoForge **21.1.234** ou plus récent](https://neoforged.net/) pour Minecraft **1.21.1**.
2. Téléchargez le dernier `mapminecraft-x.y.z.jar` depuis la page [**Releases**](../../releases).
3. Placez le `.jar` dans votre dossier `mods/`.
4. Lancez le jeu.

**Prérequis :** Minecraft 1.21.1 · NeoForge 21.1.234+ · Java 21

## 🛠️ Fabrication

Recette façonnée (4× Papier, 4× Redstone, 1× Boussole) :

```
P R P     P = Papier
R C R     R = Poudre de redstone
P R P     C = Boussole
```

## 🎮 Utilisation

1. **Posez** le Projecteur de carte vivante sur le sol.
2. Il commence immédiatement à scanner et un hologramme du terrain environnant apparaît au-dessus.
3. **Clic droit** sur le bloc pour ouvrir l'interface de réglages et ajuster le rayon, la taille, la rotation, le mode de détection, etc.
4. Reliez le bloc à de la **redstone** pour l'utiliser comme radar d'entités (le signal varie selon le nombre d'entités détectées).

> **Note :** le projecteur ne scanne que les chunks chargés. Un très grand rayon ou une cible « centrer sur coords » distante atteignant des chunks non chargés lira ces zones comme vides. La limite pratique correspond environ à la distance de rendu de votre serveur.

## 🔨 Compilation depuis les sources

```bash
git clone https://github.com/LKDM7/MapMinecraft.git
cd MapMinecraft
./gradlew build      # Windows : gradlew.bat build
```

Le jar du mod est généré dans `build/libs/mapminecraft-x.y.z.jar`.

Pour lancer un client/serveur de développement :

```bash
./gradlew runClient
./gradlew runServer
```

## 📄 Licence

Distribué sous la [Licence Apache 2.0](LICENSE).
