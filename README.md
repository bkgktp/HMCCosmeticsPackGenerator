# HMCCosmetics Pack Generator

Automatically generate resource packs and cosmetic YML files from Blockbench (.bbmodel) files for HMCCosmetics plugin.

## 🚀 Quick Start

### 1. Installation
1. Place the plugin in your `plugins/` folder
2. Start your server
3. `plugins/HMCCosmeticsPackGenerator/` folder will be created automatically

### 2. First Use
1. Put your `.bbmodel` files in `plugins/HMCCosmeticsPackGenerator/input/` folder
2. Run `/hmcpack generate` command
3. Resource pack will be generated in `plugins/HMCCosmeticsPackGenerator/output/`

## 📁 File Structure

```
plugins/HMCCosmeticsPackGenerator/
├── input/           # Place your .bbmodel files here
├── output/          # Generated resource pack appears here
├── temp/            # Temporary YML files
├── config.yml       # Plugin configuration
└── data.yml         # Model display settings
```

## 🎨 Model Naming Convention

Name your model files following this pattern:

- `samurai_helmet.bbmodel` → Helmet type cosmetic
- `dragon_backpack.bbmodel` → Backpack type cosmetic
- `knight_chestplate.bbmodel` → Chestplate type cosmetic
- `royal_boots.bbmodel` → Boots type cosmetic
- `magic_balloon.bbmodel` → Balloon type cosmetic

**Supported Types:** `helmet`, `backpack`, `chestplate`, `leggings`, `boots`, `offhand`, `balloon`

## 🎮 Commands

### Main Commands
- `/hmcpack generate` - Generate resource pack and YML files
- `/hmcpack reload` - Reload plugin configuration

### Data Management Commands
- `/hmcpack data list` - List all models
- `/hmcpack data show <model>` - Show model display settings
- `/hmcpack data set <model> <display> <property> <x> <y> <z>` - Modify model settings
- `/hmcpack data reset <model>` - Reset model to default values

### Example Usage
```
/hmcpack data set samurai_helmet head translation 0 -10 0
/hmcpack data set dragon_backpack head scale 1.2 1.2 1.2
/hmcpack data show samurai_helmet
```

## 🔧 In-Game Model Adjustments

### Real-Time Model Editing
You can adjust model positioning and scaling without restarting the server:

1. **Check Current Settings**
   ```
   /hmcpack data show samurai_helmet
   ```

2. **Adjust Position** (move model up/down/left/right)
   ```
   /hmcpack data set samurai_helmet head translation 0 -5 0
   ```

3. **Adjust Scale** (make model bigger/smaller)
   ```
   /hmcpack data set samurai_helmet head scale 1.5 1.5 1.5
   ```

4. **Adjust Rotation** (rotate model)
   ```
   /hmcpack data set samurai_helmet head rotation 0 45 0
   ```

5. **Apply Changes** (regenerate files with new settings)
   ```
   /hmcpack generate
   ```

### Display Types
- `head` - When worn on head
- `firstperson_righthand` - First person right hand view
- `thirdperson_righthand` - Third person right hand view
- `gui` - In inventory/GUI
- `ground` - When dropped on ground
- `fixed` - When placed in item frame

### Properties
- `translation` - Position offset [x, y, z]
- `rotation` - Rotation angles [x, y, z] in degrees
- `scale` - Size multiplier [x, y, z]

## ⚙️ Configuration

### config.yml
```yaml
resource-pack:
  id: 'HMCCosmetics'              # Resource pack name
  namespace: 'hmc'                # Namespace (hmc:item/...)
  transfer-to-path: ''            # Auto-copy destination path

settings:
  transfer-generated-cosmetic-yml-files: true  # Copy YML files to HMCCosmetics
  
  default-materials:              # Default material for each type
    HELMET: LEATHER_HORSE_ARMOR
    BACKPACK: LEATHER_HORSE_ARMOR
    CHESTPLATE: LEATHER_HORSE_ARMOR
    LEGGINGS: LEATHER_HORSE_ARMOR
    BOOTS: LEATHER_HORSE_ARMOR
    BALLOON: LEATHER_HORSE_ARMOR
    OFFHAND: LEATHER_HORSE_ARMOR
```

## 📤 Transfer Settings & Integration

### Automatic File Transfer
The plugin can automatically transfer generated files to other plugins or external locations:

#### 1. **HMCCosmetics Integration**
```yaml
settings:
  transfer-generated-cosmetic-yml-files: true
```
- **`true`**: Automatically copies YML files to `HMCCosmetics/cosmetics/` folder
- **`false`**: Files stay in temp folder, use `/hmcpack senddata` to transfer manually

⚠️ **Important Warning**: When set to `true`, this will **overwrite existing files** in `HMCCosmetics/cosmetics/` folder. Any manual changes you made to cosmetic YML files will be lost. Use `/hmcpack senddata` command for safer manual transfer if you have custom modifications.

#### 2. **Local File System Transfer**
```yaml
resource-pack:
  transfer-to-path: '/path/to/your/local/directory/'
```
- Automatically copies the entire resource pack to specified **local file system path**
- This is a **local computer path**, not a plugin directory
- Useful for copying to external folders, backup locations, or file sharing directories
- Leave empty (`''`) to disable

### Transfer Examples

#### Example 1: Local Backup Directory
```yaml
resource-pack:
  transfer-to-path: '/home/user/minecraft-backups/resourcepacks/'
```
Copy resource pack to a local backup directory on your computer.

#### Example 2: Web Server Directory (Linux)
```yaml
resource-pack:
  transfer-to-path: '/var/www/html/resourcepacks/'
```
Copy to your web server directory for hosting resource packs online.

#### Example 3: Windows Local Directory
```yaml
resource-pack:
  transfer-to-path: 'C:\MinecraftServer\ResourcePacks\'
```
Copy to a Windows directory (note the different path format).

#### Example 4: Network Shared Folder
```yaml
resource-pack:
  transfer-to-path: '/mnt/shared/minecraft/resourcepacks/'
```
Copy to a network shared folder accessible by multiple servers.

### Manual Transfer Commands
If automatic transfer is disabled, or if you want more control over the process:

#### YML File Transfer
- `/hmcpack senddata` - Manually send YML files from temp folder to `HMCCosmetics/cosmetics/`
- **Safer option**: This allows you to review generated files before overwriting existing ones
- **Use when**: You have custom modifications in HMCCosmetics that you don't want to lose

#### Resource Pack Transfer
- Copy resource pack manually from `output/HMCCosmetics/` folder to your desired location
- **Full control**: You decide when and where to copy the resource pack
- **Use when**: You want to test the pack before deploying it

## 🎯 Features

### ✅ Automatic Conversion
- Converts Blockbench models to Minecraft JSON format
- Automatically extracts and places textures
- Preserves display settings from original models

### ✅ Animation Support
- Creates `.mcmeta` files for animated textures
- Auto-detects animation from texture names containing `animated`, `anim`
- Supports custom animation settings from Blockbench

### ✅ Firstperson Support
- Automatically detects `model_firstperson.bbmodel` files
- Creates firstperson items for backpacks
- Links main and firstperson models automatically

### ✅ Smart Naming
- Generates display names from model filenames
- `samurai_helmet` → `Samurai Helmet`
- Maintains proper capitalization

### ✅ Dyeable Support
- Models with tintindex automatically get dyeable properties
- Adds dyeable configuration to YML files
- Supports color customization

## 🔧 Tips & Best Practices

### Model Preparation
1. Create your model in Blockbench
2. Embed textures (File → Export → Embed Textures)
3. Set display settings in Blockbench
4. Save as `.bbmodel` format

### Animated Textures
- Use `animated` or `anim` in texture names
- Example: `fire_animated.png`
- Plugin will automatically create `.mcmeta` files

### Firstperson Models
- Main model: `dragon_backpack.bbmodel`
- Firstperson: `dragon_backpack_firstperson.bbmodel`
- Plugin automatically links them together

### Data Management Workflow
1. **Generate Initial Files**: `/hmcpack generate`
2. **Test In-Game**: Check how models look
3. **Adjust Settings**: Use `/hmcpack data set` commands
4. **Regenerate**: `/hmcpack generate` to apply changes
5. **Repeat**: Until satisfied with results

## 🚨 Troubleshooting

### Model Not Processing
- Does model name contain valid type? (`helmet`, `backpack`, etc.)
- Is file extension `.bbmodel`?
- Is file in `input/` folder?
- Check console for error messages

### Textures Not Showing
- Are textures embedded in Blockbench?
- Is model file corrupted?
- Check if texture files were created in output folder

### Display Settings Wrong
- Check current settings: `/hmcpack data show <model>`
- Adjust with: `/hmcpack data set <model> <display> <property> <x> <y> <z>`
- Regenerate: `/hmcpack generate`

### Transfer Not Working
- Check `transfer-to-path` in config.yml
- Ensure destination folder exists and is writable
- Verify HMCCosmetics plugin is installed (for YML transfer)

## 📋 Permissions

- `hmcpack.generate` - Generate resource packs
- `hmcpack.reload` - Reload plugin configuration
- `hmcpack.data` - Manage model display settings
- `hmcpack.senddata` - Transfer YML files manually
- `hmcpack.*` - All permissions (default for OP)

## 🔄 Complete Workflow

1. **Model Creation**: Create model in Blockbench with proper naming
2. **File Placement**: Put `.bbmodel` file in `input/` folder
3. **Initial Generation**: Run `/hmcpack generate`
4. **Testing**: Test models in-game
5. **Adjustments**: Use data commands to fine-tune positioning
6. **Final Generation**: Run `/hmcpack generate` again
7. **Distribution**: Resource pack and YML files are ready

## 📞 Support

If you encounter issues:
1. Check console logs for error messages
2. Ensure model files follow naming conventions
3. Verify models are loaded: `/hmcpack data list`
4. Check file permissions for transfer paths
5. Ensure HMCCosmetics plugin is installed and updated

## 🔗 Integration Notes

### HMCCosmetics Compatibility
- Requires HMCCosmetics plugin to be installed
- Automatically generates compatible YML configurations
- Supports all HMCCosmetics cosmetic types
- Works with HMCCosmetics permission system

### Resource Pack Distribution
- Generated packs are compatible with Minecraft 1.21+
- Use `transfer-to-path` for automatic distribution
- Compatible with web servers, CDNs, and file sharing

---

**Note**: This plugin works alongside HMCCosmetics plugin. Make sure HMCCosmetics is installed and running.
