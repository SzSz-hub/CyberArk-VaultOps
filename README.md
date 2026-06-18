# CyberArk VaultOps

**Open-source administrator tool for managing CyberArk Self-Hosted PAM configurations and policies.**

⚠️ **Disclaimer**: This project is not affiliated with, endorsed by, or supported by CyberArk Software Ltd.

---

A JavaFX-based administration tool for CyberArk Vault configurations, designed to parse, display, and manage CyberArk policy and connection component data from XML configuration files.

## Features Overview

### Data Views

- **Connection Components**: Browse all PSM/PSMP connection components
  - See assignment across policies and device types
  - View component status (enabled/disabled)
  - Identify configuration overrides
  - Click to view detailed component XML

- **Policies**: View all access policies by device type
  - See which connection components are assigned
  - Track component enable status and overrides
  - Double-click to view full policy details

- **Usages**: Platform usage definitions
  - See policy count for each usage
  - Click to view all policies using that usage
  
- **Targets**: Aggregated target addresses from connection components
  - View effective target addresses mapped in PVConfiguration.xml
  - Track source vs. altered target addresses
  
- **PSM/PSMP Servers**: Platform Security Manager instances
  - PSM server addresses, ports, and TS Gateway configuration
  - PSMP server mappings and connectivity info

### Source Profile Management

Manage multiple environment configurations:

- Add/edit/delete source profiles (Production, Test, Dev, etc.)
- Quick switching with drag-reorderable sidebar
- Per-environment data caching and load tracking
- Settings dialog for:
  - Display name and short label
  - Folder path to XML files
  - Automatic persistence

### File Monitoring

- **Update Current**: Reload active tab's data from disk
- **Reload All**: Clear all caches for current environment
- **Stale File Detection**: Automatic monitoring
  - Shows file load timestamp in status bar
  - Red indicator when source file modified after load
  - Allows manual refresh when upstream files change

### Performance & UX

- **Lazy Loading**: Tabs only load data when first selected
- **Filtering**: Search fields on all table columns
- **Per-Environment Caching**: Load state isolated by source profile
- **Auto-Load**: Active tab populates on startup if config exists
- **Immediate UI Feedback**: Double-click for details, single-click for side panel updates

## Theme Support

JavaFX CSS themes for customization:

### Built-in Themes

- `light` - Clean light background
- `dark` - Dark background with light text
- `midnight` - Deep dark with accent colors
- `forest` - Green-tinted professional theme
- `high-contrast` - High contrast for accessibility

### Theme Customization

Built-in themes packaged under `src/main/resources/themes`.
External editable themes loaded from `themes/` folder next to `app.properties`.

**Use Theme Menu:**
- **Switch themes**: `Settings > Theme > [theme name]`
- **Preview themes**: `Settings > Theme > Theme preview...`
- **Refresh**: `Settings > Theme > Refresh themes` (after editing CSS files)
- **Open folder**: `Settings > Theme > Open themes folder`

**Create Custom Theme:**

1. Open external `themes/` folder
2. Copy `example.css` and rename (e.g., `my-team-theme.css`)
3. Adjust CSS variables and styles
4. Refresh themes from Settings menu

The filename becomes the theme ID (e.g., `my-team-theme.css` → `my-team-theme`).

The app applies `base.css` first, then selected theme CSS (custom themes can override selectively).

## Configuration

Settings stored in `app.properties` next to executable/jar:

```ini
# Active profile ID
activeProfile=profile-id

# Profile definitions
profile.profile-id.displayName=Production
profile.profile-id.shortLabel=Prod
profile.profile-id.folderPath=C:\\path\\to\\lpc\\files

# Profile order
profiles.order=prod-id,test-id,dev-id

# Active theme
theme=dark
```

## Build & Run

**Prerequisites:** Java 17+, Maven

### Build

```powershell
mvn clean package
```

### Run During Development

```powershell
mvn javafx:run
```

### Run Packaged JAR

```powershell
Set-Location "C:\path\to\CyberArkAdminTool"
java -jar .\target\CyberArkAdminTool-1.0-SNAPSHOT.jar
```

### Application Icon & Native Packaging

The window icon is drawn at runtime by `AppIcon` (a vault dial + keyhole), so it appears in the
title bar and alt-tab switcher of the running app — just rebuild and relaunch to see it.

On Windows, the **taskbar button** and the **executable's own file icon** are taken from the
launching program, not from `stage.getIcons()`. To get the icon everywhere, build a native
app-image with the bundled profile (requires JDK `jpackage`, included with JDK 17+):

```powershell
mvn -P native-package clean package
# Launch the native build:
& ".\target\dist\CyberArk VaultOps\CyberArk VaultOps.exe"
```

This generates `target/app-icon.ico` (and PNGs) from the same design via `IconExporter`, then runs
`jpackage` to embed it. You can also regenerate the assets on their own:

```powershell
mvn -q compile
java -cp target/classes IconExporter target
```

## XML File Requirements

### PVConfiguration.xml

```xml
<PVConfiguration>
  <ConnectionComponents>
    <ConnectionComponent Id="comp-id" Name="Component Name">
      <UserParameters>
        <Parameter Name="PSMRemoteMachine" Value="target-address" />
      </UserParameters>
    </ConnectionComponent>
  </ConnectionComponents>
  <PSMServers>
    <PSMServer ID="id" Name="Name" Address="address" Port="1234" 
               TSGatewayAddress="gateway" TSGatewayEnable="yes" />
  </PSMServers>
  <PSMPServers>
    <PSMPServer ID="id" Name="Name" Address="address" Port="8080" />
  </PSMPServers>
</PVConfiguration>
```

### Policies.xml

```xml
<PasswordVaultPolicies>
  <Devices>
    <Device Name="DeviceType">
      <Policies>
        <Policy ID="policy-id" Enabled="yes">
          <ConnectionComponents>
            <ConnectionComponent Id="comp-id" Enable="yes">
              <UserParameters>
                <Parameter Name="PSMRemoteMachine" Value="address" />
              </UserParameters>
            </ConnectionComponent>
          </ConnectionComponents>
          <Usages>
            <Usage Name="usage-name" />
          </Usages>
        </Policy>
      </Policies>
    </Device>
  </Devices>
  <Usages>
    <Usage ID="usage-id" PlatformBaseID="platform" 
           PlatformBaseType="WindowsServer" PlatformBaseProtocol="RDP" />
  </Usages>
</PasswordVaultPolicies>
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "No active source" | Open Settings > Sources, add profile with valid folder path |
| XML files not loading | Verify PVConfiguration.xml and Policies.xml exist in configured folder |
| Stale indicator persists | Click "Reload All" to refresh file modification timestamps |
| Slow table scrolling on large datasets | Filtering is responsive; use search to narrow results |

## Architecture

- **AppController**: Loads data, manages caching, coordinates UI updates
- **AppSettings**: Stores user profiles and preferences
- **PoliciesParser**: DOM-based Policies.xml parser
- **PVConfigurationParser**: DOM-based PVConfiguration.xml parser
- **UI**: JavaFX components (TableView, SplitPane, TreeView)
- **SideNav**: Profile selection sidebar with drag-reorder
- **ThemeManager**: CSS theme loading and switching

## Performance Notes

- Lazy loading minimizes startup time
- Large XML files load on first tab access
- Filtering on typed columns is responsive
- Per-environment caching reduces repeated parsing
- File staleness checks are lightweight (file metadata only)

