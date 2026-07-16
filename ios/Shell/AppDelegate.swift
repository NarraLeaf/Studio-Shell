import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?
    private var config = ShellConfig.defaultConfig

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        config = ShellConfig.load()

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = ShellViewController(config: config)
        window.backgroundColor = config.backgroundColor
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    /// The orientation whitelist in Info.plist is the outer bound; the injected
    /// config narrows it per game without the repacker having to touch the
    /// plist's supported-orientation array at install time.
    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        config.orientation.mask
    }
}
