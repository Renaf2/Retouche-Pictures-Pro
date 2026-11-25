<div align="center">

# 🖼️ Retouche Pictures **Pro**

### L'éditeur photo Android qui travaille *pour vous*, pas pour des annonceurs.

**Aucune pub · Aucune donnée envoyée · Aucune permission Internet · 100 % hors ligne**

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%20Design-3-6750A4?logo=materialdesign&logoColor=white)
![ONNX Runtime](https://img.shields.io/badge/IA-ONNX%20Runtime%201.32-005CED?logo=onnx&logoColor=white)
![Internet](https://img.shields.io/badge/permission%20Internet-aucune-success)
![Publicités](https://img.shields.io/badge/publicit%C3%A9s-0-success)

</div>

---

## ✨ En bref

**Retouche Pictures Pro** est une application de retouche photo complète, pensée pour être **simple pour les enfants comme pour les seniors** : grands boutons, texte lisible, onglets en capsules, design Material moderne. Tout le traitement, y compris la **suppression du fond par intelligence artificielle**, se fait **sur le téléphone**. Vos images ne le quittent jamais.

> **Pro** = version embarquant le modèle IA haute précision **IS-Net** (≈ 170 Mo). La version « légère » utilise le petit modèle U²-Netp (≈ 4,5 Mo). Les deux ont le même code ; seule change la présence du fichier modèle.

## 🔒 Confidentialité, garantie par le code

| | |
|---|---|
| 🚫 **Pas de permission `INTERNET`** | Le manifeste ne déclare **aucune** permission : Android empêche techniquement l'application d'accéder au réseau. |
| 🚫 **Pas de pub, d'analytics ni de crash reporting** | Aucune bibliothèque tierce de ce type. |
| 📂 **Fichiers via le sélecteur Android** | Pas d'accès global à la galerie. |
| 💾 **`allowBackup="false"`** | Rien n'est copié vers un cloud. |

Vérification en 10 secondes : dans Android Studio, ouvrez l'onglet **Merged Manifest** de `AndroidManifest.xml` : il ne doit afficher aucun `uses-permission`.

## 🧰 Fonctions

La barre du bas range tout en **6 familles**, puis les outils de la famille apparaissent en capsules, et les options de l'outil juste au-dessus (repliables).

| Famille | Ce que l'on y trouve |
|---|---|
| 🖌️ **Dessiner** | Pinceau (crayon, stylo, feutre, surligneur, aérographe), gomme, pot de peinture, pipette, formes (ligne, flèche, rectangle, ellipse, pleines ou non) |
| 🩹 **Retoucher** | Tampon de clonage, correcteur de taches, flou au doigt, pixellisation au doigt (visages, plaques) |
| 🎚️ **Réglages** | Luminosité, contraste, saturation, chaleur, netteté, vignettage ; 9 filtres en un geste avec intensité |
| ➕ **Ajouter** | Texte (polices, gras, italique, contour, ombre), image sur image (déplacer, agrandir et tourner à deux doigts, opacité, miroir) |
| ✂️ **Découper** | Recadrage (libre, 1:1, 4:3, 16:9, 9:16, A4…), sélection (rectangle, ellipse, lasso), **fond transparent**, pinceau « Restaurer » |
| 🧱 **Calques** | Miniatures, visibilité, dupliquer, ordre, fusion, opacité, modes de fusion |

### 🪄 Fond transparent

1. **Détourage automatique par IA** (IS-Net ou U²-Netp), hors ligne.
2. **Baguette magique** : tolérance réglable, zone connectée ou couleur partout.
3. **Pinceau « Restaurer »** pour récupérer ce que l'IA a trop effacé.
4. **Affiner le contour** : rogner le liseré, adoucir.
5. **Nouveau fond** : couleur, image de la galerie ou photo d'origine floutée.

### 📐 Redimensionnement automatique

Avatar 256/512 · Icônes 48/96/192/512 · Fond d'écran (aux dimensions de votre écran, portrait ou paysage) · A4 portrait/paysage/auto · Full HD · Story · taille libre. Modes **Ajuster**, **Remplir** ou **Étirer**.

### 🧱 Un vrai système de calques

Texte, images ajoutées et zones coupées sont des **calques** : déplaçables et modifiables à tout moment. **Annuler / rétablir** couvre toutes les opérations.

### ⚙️ Roue dentée

Ouvrir · Nouvelle image · Enregistrer (PNG, JPEG, WebP) · Changer la taille · Pivoter · Miroir · Rogner les bords vides · À propos. Vous pouvez aussi **partager une image vers l'application** depuis la galerie.

## 🚀 Installation

### Prérequis

- **Android Studio** récent (Koala ou plus)
- Un téléphone Android 8.0+ (débogage USB) ou un émulateur
- Internet **uniquement pour compiler** (Gradle) ; l'application, elle, n'en a jamais besoin

### Compiler

```text
1. File > Open…  → choisir le dossier du projet
2. Laisser la synchronisation Gradle se terminer
3. Brancher le téléphone, puis ▶ Run
```

APK installable : `Build > Build Bundle(s) / APK(s) > Build APK(s)`, variante **release** (signée avec la clé de debug, suffisant pour un usage personnel).

> Le dépôt ne contient pas `gradlew`. Android Studio n'en a pas besoin ; pour la ligne de commande, lancez une fois `gradle wrapper`.

## 🧠 Modèles d'IA (détourage)

Les fichiers `.onnx` se placent dans `app/src/main/assets/`. **Le plus précis présent est choisi automatiquement.**

| Fichier | Taille | Qualité | Édition |
|---|---|---|---|
| `isnet-general-use.onnx` | ≈ 170 Mo | ⭐⭐⭐⭐ la meilleure | **Pro** |
| `u2net.onnx` | ≈ 170 Mo | ⭐⭐⭐ | optionnel |
| `silueta.onnx` | ≈ 43 Mo | ⭐⭐ | optionnel |
| `u2netp.onnx` | ≈ 4,5 Mo | ⭐ léger | **Léger** (fourni) |

Source des modèles : [rembg, release v0.0.0](https://github.com/danielgatis/rembg/releases/tag/v0.0.0). Moteur : [ONNX Runtime](https://onnxruntime.ai/) 1.32.0 (compatible pages mémoire de 16 Ko).

> ⚠️ GitHub refuse les fichiers de plus de 100 Mo : pour publier la version Pro, n'ajoutez pas `isnet-general-use.onnx` au dépôt (utilisez [Git LFS](https://git-lfs.com/) ou une *release*, ou ajoutez-le à `.gitignore`) et documentez son téléchargement.

## 🗂️ Structure du code

```text
app/src/main/java/com/retouche/app/
├── MainActivity.kt        point d'entrée, ouverture / partage d'images
├── EditorScreen.kt        interface Compose, gestes, dialogues, À propos
├── EditorViewModel.kt     état, outils, historique d'annulation
├── Model.kt               calques, document, sélection, formes
├── Compositor.kt          assemblage des calques (écran, export, miniatures)
├── DocOps.kt              recadrage, rotation, miroir, taille sur tous les calques
├── Fx.kt                  réglages, filtres, flou, netteté, correcteur, formes…
├── ImageOps.kt            pinceaux, remplissage, baguette, texte
├── BgRemover.kt           détourage par réseau de neurones (ONNX)
├── ImageIO.kt             ouverture (EXIF) et enregistrement
└── Theme.kt               thème Material 3, formes, typographie
```

## 📏 Limites connues

- Images importées ramenées à **4096 px** max sur le plus grand côté (fluidité et mémoire).
- Pot de peinture : sur des bords très adoucis, augmentez la tolérance pour éviter un liseré.
- L'aperçu du vignettage porte sur l'ensemble des calques visibles ; l'application finale n'affecte que le calque actif.

## 🙏 Crédits

- [U²-Net](https://github.com/xuebinqin/U-2-Net) et [IS-Net / DIS](https://github.com/xuebinqin/DIS) (Apache 2.0)
- [rembg](https://github.com/danielgatis/rembg) pour la publication des modèles ONNX
- [ONNX Runtime](https://onnxruntime.ai/), [Jetpack Compose](https://developer.android.com/jetpack/compose)

---

<div align="center">

Fait avec ❤️ pour que retoucher une photo reste simple, rapide et privé.

</div>
