pub const TARGET_MINECRAFT_VERSION: &str = "26.1.2";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LaunchProfile {
    pub minecraft_version: String,
    pub game_directory: String,
    pub java_executable: String,
    pub max_memory_mb: u32,
    pub loader_jar: String,
    pub node_runtime_entry: String,
}

impl Default for LaunchProfile {
    fn default() -> Self {
        Self {
            minecraft_version: TARGET_MINECRAFT_VERSION.to_string(),
            game_directory: ".akivcraft/game".to_string(),
            java_executable: "java".to_string(),
            max_memory_mb: 4096,
            loader_jar: "loader-java/build/libs/akivcraft-loader.jar".to_string(),
            node_runtime_entry: "node-runtime/dist/index.js".to_string(),
        }
    }
}

impl LaunchProfile {
    pub fn main_class(&self) -> &'static str {
        "dev.akivcraft.loader.AkivCraftMain"
    }

    pub fn classpath_entries(&self) -> Vec<String> {
        vec![self.loader_jar.clone()]
    }

    pub fn jvm_args(&self) -> Vec<String> {
        vec![
            format!("-Xmx{}M", self.max_memory_mb),
            format!("-Dakivcraft.nodeRuntime={}", self.node_runtime_entry),
            format!("-Dakivcraft.minecraftVersion={}", self.minecraft_version),
        ]
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InstallState {
    NotInstalled,
    Installed { version: String },
    NeedsRepair { reason: String },
}

pub fn default_install_state() -> InstallState {
    InstallState::NotInstalled
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_profile_targets_current_version() {
        let profile = LaunchProfile::default();

        assert_eq!(profile.minecraft_version, TARGET_MINECRAFT_VERSION);
        assert!(profile.jvm_args().iter().any(|arg| arg == "-Dakivcraft.minecraftVersion=26.1.2"));
        assert_eq!(profile.main_class(), "dev.akivcraft.loader.AkivCraftMain");
        assert!(profile.jvm_args().iter().all(|arg| !arg.contains("agent")));
    }
}
