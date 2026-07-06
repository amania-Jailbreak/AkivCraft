use akivcraft_launcher_core::{default_install_state, InstallState, LaunchProfile, TARGET_MINECRAFT_VERSION};
use gpui::{prelude::*, *};
use gpui_platform::application;

struct LauncherApp {
    profile: LaunchProfile,
    install_state: InstallState,
}

impl LauncherApp {
    fn new() -> Self {
        Self {
            profile: LaunchProfile::default(),
            install_state: default_install_state(),
        }
    }

    fn install_label(&self) -> String {
        match &self.install_state {
            InstallState::NotInstalled => "Not installed".to_string(),
            InstallState::Installed { version } => format!("Installed: {version}"),
            InstallState::NeedsRepair { reason } => format!("Needs repair: {reason}"),
        }
    }
}

impl Render for LauncherApp {
    fn render(&mut self, _window: &mut Window, _cx: &mut Context<Self>) -> impl IntoElement {
        div()
            .size_full()
            .bg(rgb(0x10131a))
            .text_color(rgb(0xe9edf5))
            .p_8()
            .flex()
            .flex_col()
            .gap_4()
            .child(div().text_3xl().font_weight(FontWeight::BOLD).child("AkivCraft"))
            .child(div().text_lg().child(format!("Minecraft Java {TARGET_MINECRAFT_VERSION}")))
            .child(div().child(self.install_label()))
            .child(div().child(format!("Memory: {} MB", self.profile.max_memory_mb)))
            .child(
                div()
                    .mt_4()
                    .px_4()
                    .py_2()
                    .rounded_md()
                    .bg(rgb(0x4f7cff))
                    .text_color(rgb(0xffffff))
                    .child("Launch"),
            )
    }
}

fn main() {
    application().run(|cx: &mut App| {
        let bounds = Bounds::centered(None, size(px(920.0), px(580.0)), cx);
        cx.open_window(WindowOptions { window_bounds: Some(WindowBounds::Windowed(bounds)), ..Default::default() }, |_window, cx| {
            cx.new(|_cx| LauncherApp::new())
        })
        .expect("failed to open AkivCraft launcher window");

        cx.activate(true);
    });
}
