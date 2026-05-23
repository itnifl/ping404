# PlantUML Setup

This project uses PlantUML diagrams.

## Prerequisites

- Java (JDK or JRE)

## VS Code

1. Install the [Plan UML](https://marketplace.visualstudio.com/items?itemName=justuskarlsson.plan-uml) extension
2. Run the setup script from the workspace root:

```powershell
.\setup-plantuml.ps1
```

3. Open any `.puml` file and use the extension's preview to render diagrams

## Android Studio

1. Install the [PlantUML Integration](https://plugins.jetbrains.com/plugin/7017-plantuml-integration) plugin
2. Go to **Settings > Other Settings > PlantUML**
3. Set the PlantUML JAR path to `plantuml-1.2025.10.jar` in your workspace root
4. Open any `.puml` file and use the tool window to render diagrams
