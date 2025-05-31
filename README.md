# 🚀 Resource Updater Utility  
**A simple Minecraft Fabric mod to automatically prepare resources (packs and mods) for players**

*Forge and Neoforge portings will soon be released (feel pleased to any PR!)*
*However I don't have the time to make Quilt and Liteloader portings haha*

## ✨ **Features**  

### 🎨 **Download Interface**
- **Real-time progress tracking**: Percentage, speed, and ETA indicators  
- **User-friendly controls**: Pause/resume downloads on demand  
- **Visual polish**: Clean, intuitive GUI designed for Minecraft’s aesthetic  

### 🔐 **Security**  
- **Secure encryption**: Protection for all transfers  
- **Tamper-proof verification**: Ensures file integrity  
- **Optional checksums**: SHA-1 validation for resources

### ⚡ **Performance Optimized**  
- **Multi-threaded downloads**: Faster transfers without lag  
- **Smart caching**: Reduces redundant downloads  
- **Bandwidth control**: Configurable speed limits  


## 📋 **Compatibility**  
| Component       | Requirement                                                                   |  
|-----------------|-------------------------------------------------------------------------------|  
| Minecraft       | 1.17.1-1.21.5 (But new features are only first published on 1.20.1)           |  
| Mod Loader      | Fabric, Neoforge and Forge (Later two is not supported yet)                   |  
| Java            | Version 17+                                                                   |  
| OS              | Windows, Linux, macOS                                                         |  


## 🛠️ **Installation**  
### **Client Setup**  
1. Download the mod JAR from Github Actions 
2. Place in `.minecraft/mods/`  
3. Launch Minecraft


## 🔄 **How It Works**  
1. **Server** hosts resources metadata
2. **Client** detects metadata automatically on starting client  
3. **User** waits download via a sleek GUI  
4. **System** decrypts and installs seamlessly


## ⚙️ **Configuration**  
Edit `config/resourcepackupdater.json` (Haven't completed this method yet):  
```json
{
  "max_bandwidth": "10Mbps",
  "force_download": false,
  "enable_checksum": true
}
```
> **Warning**: Server-side PHP required for full functionality *(host in a separate repo in my Github)*.  


## ❓ **FAQ**  

### **Why don't support Minecraft 1.16.5?**  
This project uses Java 8+ functions and Minecraft makes great changes in GL from 1.16.5 to 1.17,1, which makes backports harder to finish. If any Pull Request can be handed I will sincerely thank you.   

### **Can players skip downloads?**  
Yes! Just press ESC. (But nobody do this uhn? Without mods can they join the modded server?)  

### **How to update packs?**  
Replace the server’s files and clients auto-update on restart.  


## ⚠️ **Security Best Practices**  
- **Always** use HTTPS for downloads.  
- **Never** disable checksum validation in production.  


## 📜 **License**  
MIT License

## 📥 **Download**  
Get the latest release in Github Actions.

*Releases (Whatever on Github or Modrinth or Curseforge) will not be published until this mod is stable enough.*
