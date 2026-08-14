import SwiftUI
import UIKit
import SampleShared

/// Bridges the Compose root into SwiftUI.
///
/// `MainViewController()` installs Scry and returns the sample UI; everything
/// else — including presenting the Scry window when `Scry.show()` is called —
/// is handled inside the shared module.
struct ComposeRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        SampleSharedKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

@main
struct ScrySampleApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRoot().ignoresSafeArea(.keyboard)
        }
    }
}
