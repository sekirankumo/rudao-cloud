import Shared
import SwiftUI
import UIKit

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            // Compose draws edge to edge and handles the safe areas itself, like on Android.
            ComposeView()
                .ignoresSafeArea(.all)
                .background(Color("LaunchBackground"))
        }
    }
}
