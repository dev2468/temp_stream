# Stream Chat Android App# Stream Chat Android App



A modern Android chat application built with **Stream Chat SDK**, **Firebase Authentication**, **Jetpack Compose**, and **MVVM architecture**.A modern Android chat application built with **Stream Chat SDK**, **Jetpack Compose**, and **MVVM architecture**.



## 📱 Features## 🚀 Features



### Authentication### Authentication

- ✅ Firebase Authentication (email/password)- ✅ User login with username/token

- ✅ Session persistence with auto-reconnection- ✅ Demo account quick access

- ✅ Secure logout with state cleanup- ✅ Session persistence with auto-reconnection

- ✅ Secure logout

### Channels

- ✅ Real-time channel list with unread counts### Channels

- ✅ Channel search functionality- ✅ Real-time channel list

- ✅ Create 1:1 and group chats- ✅ Unread message counts & badges

- ✅ Optional group naming- ✅ Channel search

- ✅ Last message preview with smart timestamps- ✅ Last message preview

- ✅ Smart timestamp formatting

### Messaging

- ✅ Send/receive text messages in real-time### Messaging

- ✅ Image attachments with preview- ✅ Send/receive text messages

- ✅ Message reactions (emoji)- ✅ Image attachments with preview

- ✅ Typing indicators- ✅ Message reactions (emoji)

- ✅ Message timestamps- ✅ Typing indicators

- ✅ Debounced updates to prevent rate limiting- ✅ Real-time message updates

- ✅ Message timestamps

### Group Management

- ✅ Set group name during creation### UI/UX

- ✅ View group details (members, created date)- ✅ Material Design 3

- ✅ Rename groups after creation- ✅ Dark mode support

- ✅ Support multiple groups with same members- ✅ Responsive layouts

- ✅ Loading states

### UI/UX- ✅ Error handling

- ✅ Material Design 3- ✅ Empty states

- ✅ Dark mode support

- ✅ Responsive Compose layouts## 📱 Screenshots

- ✅ Loading states & error handling

- ✅ Empty states with helpful messages| Splash Screen | Login | Channel List | Messages |

|---------------|-------|--------------|----------|

---| Loading... | Enter credentials | Your channels | Chat view |



## 🏗️ Architecture## 🏗️ Architecture



This app follows **Clean MVVM (Model-View-ViewModel)** architecture:This app follows **MVVM (Model-View-ViewModel)** architecture pattern:



``````

app/src/main/java/com/example/streamchat/┌──────────────────────────────────────────────────────┐

├── data/│                   Presentation Layer                  │

│   └── repository/│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐ │

│       └── ChatRepository.kt           # Session & token management│  │  Activity   │  │  Activity   │  │   Activity   │ │

├── ui/│  │  (Compose)  │  │  (Compose)  │  │   (Compose)  │ │

│   ├── auth/│  └──────┬──────┘  └──────┬──────┘  └───────┬──────┘ │

│   │   └── FirebaseAuthViewModel.kt   # Firebase auth logic│         │                │                  │         │

│   ├── channels/│         ▼                ▼                  ▼         │

│   │   └── ChannelListViewModel.kt    # Channel queries & creation│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐ │

│   ├── messages/│  │  ViewModel  │  │  ViewModel  │  │   ViewModel  │ │

│   │   └── MessageListViewModel.kt    # Message operations & reactions│  │  (StateFlow)│  │  (StateFlow)│  │  (StateFlow) │ │

│   └── ViewModelFactory.kt            # ViewModel factory│  └──────┬──────┘  └──────┬──────┘  └───────┬──────┘ │

├── ChatApplication.kt                  # App initialization, SDK setup└─────────┼─────────────────┼──────────────────┼───────┘

├── MainActivity.kt                     # Splash screen & routing          │                 │                  │

├── LoginActivity.kt                    # Firebase login UI          └─────────────────┼──────────────────┘

├── ChannelListActivity.kt              # Channel list with search                            ▼

└── MessageListActivity.kt              # Chat screen with composer┌──────────────────────────────────────────────────────┐

```│                     Data Layer                        │

│              ┌───────────────────┐                    │

### Layer Responsibilities│              │   ChatRepository  │                    │

│              │ (Session Manager) │                    │

**Data Layer**│              └─────────┬─────────┘                    │

- `ChatRepository`: Manages user sessions, tokens, and SharedPreferences persistence│                        │                              │

- Handles Stream ChatClient token refresh and reconnection│                        ▼                              │

│              ┌───────────────────┐                    │

**ViewModel Layer**│              │  Stream Chat SDK  │                    │

- `FirebaseAuthViewModel`: Firebase authentication (sign in, sign up)│              │    (ChatClient)   │                    │

- `ChannelListViewModel`: Channel queries, search, creation, and event subscriptions│              └───────────────────┘                    │

- `MessageListViewModel`: Message CRUD, reactions, typing indicators, and debounced refresh└──────────────────────────────────────────────────────┘

```

**Presentation Layer**

- Activities use Jetpack Compose for UI### Key Components

- Collect StateFlow/LiveData from ViewModels

- Minimal business logic, pure presentation#### Data Layer

- **ChatRepository**: Manages user sessions, tokens, and persistence

---

#### ViewModel Layer

## 🛠️ Tech Stack- **LoginViewModel**: Handles authentication logic

- **ChannelListViewModel**: Manages channel queries and search

### Android- **MessageListViewModel**: Controls message operations and real-time updates

- **Language**: Kotlin 1.9.22

- **UI**: Jetpack Compose + Material Design 3#### Presentation Layer

- **Architecture**: MVVM with StateFlow/LiveData- **MainActivity**: Splash screen and routing

- **Min SDK**: 24 (Android 7.0)- **LoginActivity**: Authentication UI

- **Target SDK**: 35 (Android 15)- **ChannelListActivity**: Channel list with search

- **MessageListActivity**: Message list and composer

### Stream Chat SDK

- **Version**: 6.0.13## 🛠️ Tech Stack

- **Modules**:

  - `stream-chat-android-client` - Core client### Core Technologies

  - `stream-chat-android-state` - State management- **Kotlin**: 100% Kotlin codebase

  - `stream-chat-android-offline` - Offline support- **Stream Chat SDK**: 6.5.4

  - `stream-chat-android-compose` - Compose UI components  - Client, State, Offline plugins

  - UI Components & Compose support

### Firebase- **Jetpack Compose**: Modern declarative UI

- **Firebase Authentication**: Email/password sign-in- **Material Design 3**: Modern Android UI guidelines

- **firebase-admin** (backend): Verify ID tokens

### Architecture Components

### Backend (Node.js Token Server)- **ViewModel**: Lifecycle-aware state management

- **Express.js**: REST API- **StateFlow**: Reactive data streams

- **firebase-admin**: Verify Firebase ID tokens- **Coroutines**: Asynchronous operations

- **stream-chat**: Generate Stream user tokens- **Repository Pattern**: Data abstraction layer

- **Dependencies**: cors, helmet, express-rate-limit, dotenv

### Additional Libraries

---- **Coil**: Image loading (2.5.0)

- **SharedPreferences**: Local storage

## 🚀 Getting Started

## 📋 Prerequisites

### Prerequisites

1. **Stream Chat Account**: Get API key and secret from [getstream.io](https://getstream.io)- Android Studio Ladybug | 2024.2.1 or newer

2. **Firebase Project**: Set up at [console.firebase.google.com](https://console.firebase.google.com)- JDK 17

3. **Node.js**: v18+ for the token server- Android SDK 24+ (Android 7.0+)

4. **Android Studio**: Latest stable version- Gradle 8.7



### Setup Steps## 🔧 Setup Instructions



#### 1. Clone Repository### 1. Clone the Repository

```bash```bash

git clone <your-repo-url>git clone <repository-url>

cd streamchatcd streamchat

``````



#### 2. Configure Firebase### 2. Open in Android Studio

1. Create a Firebase project- Open Android Studio

2. Enable **Email/Password** authentication- Select "Open an Existing Project"

3. Download `google-services.json` and place in `app/`- Navigate to the cloned directory

4. Download **Service Account Key** for backend:

   - Firebase Console → Project Settings → Service Accounts### 3. Configure API Key

   - Generate new private key → save as `serviceAccountKey.json`The app uses Stream Chat API key: `zrqhgvpgnjrc`



#### 3. Configure Backend Token ServerTo use your own API key:

```bash1. Get an API key from [GetStream.io](https://getstream.io/chat/)

cd backend/token-server2. Open `ChatApplication.kt`

npm install3. Replace the API key:

``````kotlin

ChatClient.Builder("YOUR_API_KEY_HERE", applicationContext)

Edit `.env`:```

```env

STREAM_KEY=your_stream_api_key### 4. Sync & Build

STREAM_SECRET=your_stream_secret- Click "Sync Project with Gradle Files"

PORT=8080- Wait for dependencies to download

GOOGLE_APPLICATION_CREDENTIALS=C:/path/to/serviceAccountKey.json- Build the project (Build → Make Project)

```

### 5. Run the App

Start the server:- Connect an Android device or start an emulator

```bash- Click "Run" or press Shift+F10

npm start

```## 🧪 Testing



Server runs on `http://localhost:8080`### Demo Account

The app includes a demo account for quick testing:

#### 4. Configure Android App- Click "Login with Demo Account" on the login screen

Update `app/src/main/res/values/strings.xml`:- Automatically logs in and navigates to channel list

```xml

<string name="backend_base_url">http://10.0.2.2:8080</string> <!-- Emulator -->### Custom Login

<!-- For physical device, use your PC's LAN IP: http://192.168.x.x:8080 -->To log in with your own user:

<string name="stream_api_key">your_stream_api_key</string>1. Enter a User ID (e.g., "john-doe")

```2. Enter a User Token (get from Stream Dashboard)

3. Optionally enter a username

#### 5. Build & Run4. Click "Login"

```bash

./gradlew assembleDebug## 📁 Project Structure

# Or use Android Studio Run button

``````

streamchat/

---├── app/

│   ├── src/main/

## 📡 Backend API│   │   ├── java/com/example/streamchat/

│   │   │   ├── ChatApplication.kt              # App entry point

### Endpoints│   │   │   ├── MainActivity.kt                 # Splash & routing

│   │   │   ├── LoginActivity.kt                # Auth screen

#### `GET /health`│   │   │   ├── ChannelListActivity.kt          # Channel list

Health check endpoint.│   │   │   ├── MessageListActivity.kt          # Chat screen

│   │   │   │

**Response**:│   │   │   ├── data/repository/

```json│   │   │   │   └── ChatRepository.kt           # Session management

{│   │   │   │

  "ok": true,│   │   │   └── ui/

  "key": true│   │   │       ├── ViewModelFactory.kt         # ViewModel factory

}│   │   │       ├── login/

```│   │   │       │   └── LoginViewModel.kt

│   │   │       ├── channels/

#### `GET /token`│   │   │       │   └── ChannelListViewModel.kt

Issues a Stream Chat user token after verifying Firebase ID token.│   │   │       └── messages/

│   │   │           └── MessageListViewModel.kt

**Headers**:│   │   │

```│   │   ├── res/

Authorization: Bearer <firebase_id_token>│   │   │   ├── drawable/                       # Icons & drawables

```│   │   │   ├── values/                         # Themes, colors, strings

│   │   │   └── mipmap/                         # App icons

**Response**:│   │   │

```json│   │   └── AndroidManifest.xml

{│   │

  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."│   └── build.gradle.kts                        # App dependencies

}│

```├── gradle/

│   └── libs.versions.toml                      # Version catalog

### Authentication Flow│

1. User signs in with Firebase (Android app)├── build.gradle.kts                            # Root build config

2. App gets Firebase ID token├── settings.gradle.kts                         # Project settings

3. App calls `/token` with ID token in `Authorization` header├── README.md                                   # This file

4. Backend verifies ID token via Firebase Admin SDK├── PROJECT_STRUCTURE.md                        # Detailed structure

5. Backend creates/upserts user in Stream└── REFACTORING_SUMMARY.md                      # Architecture details

6. Backend returns Stream user token```

7. App connects to Stream Chat with token

## 🔑 Key Files

---

### Application

## 🎯 Key Features Explained- **ChatApplication.kt**: SDK initialization, auto-reconnection



### Real-time Updates with Debouncing### Activities (Presentation)

To prevent Stream API rate limiting (429 errors), we use debounced event-driven updates:- **MainActivity.kt**: Splash screen with ChatClient state monitoring

- **LoginActivity.kt**: Authentication with ViewModel integration

```kotlin- **ChannelListActivity.kt**: Channel list with search functionality

// ViewModels subscribe to Stream events- **MessageListActivity.kt**: Message list with composer and reactions

chatClient.subscribe { event ->

    when (event) {### ViewModels (Business Logic)

        is NewMessageEvent, is MessageReadEvent -> {- **LoginViewModel.kt**: Login state (`LoginUiState`)

            _refreshSignals.tryEmit(Unit)- **ChannelListViewModel.kt**: Channel queries (`ChannelListUiState`)

        }- **MessageListViewModel.kt**: Message operations (`MessageListUiState`)

    }

}### Repository (Data)

- **ChatRepository.kt**: User session management with SharedPreferences

// Debounce refresh signals

_refreshSignals### Factory (DI)

    .debounce(1000) // Wait 1s before refreshing- **ViewModelFactory.kt**: Creates ViewModels with dependencies

    .onEach { loadChannels() }

    .launchIn(viewModelScope)## 🎯 Features Breakdown

```

### Session Management

### Group Management```kotlin

- **1:1 chats**: Use deterministic channel ID (sorted member IDs) to prevent duplicates// Auto-reconnection on app restart (ChatApplication.kt)

- **Group chats**: Use random UUID to allow multiple groups with same membersval user = repository.getCurrentUser()

- **Naming**: Store group name in `channel.extraData["name"]`if (user != null) {

- **Rename**: Call `ChatClient.updateChannel(type, id, null, mapOf("name" to newName))`    val tokenProvider = repository.getTokenProvider()

    ChatClient.instance().connectUser(user, tokenProvider).enqueue()

### Lifecycle-Aware Coroutines}

- Use `rememberCoroutineScope()` in Compose for user-triggered actions```

- Coroutines auto-cancel when Composable leaves composition

- No memory leaks or crashes from disposed state updates### Real-Time Channels

```kotlin

---// Live channel updates (ChannelListViewModel.kt)

client.queryChannels(request).watch().collect { result ->

## 📂 Project Structure    _uiState.value = ChannelListUiState.Success(result.data())

}

``````

streamchat/

├── app/### Reactive UI

│   ├── src/main/```kotlin

│   │   ├── java/com/example/streamchat/// StateFlow with Compose (Activity)

│   │   │   ├── data/repository/val uiState by viewModel.uiState.collectAsStateWithLifecycle()

│   │   │   │   └── ChatRepository.kt

│   │   │   ├── ui/when (uiState) {

│   │   │   │   ├── auth/FirebaseAuthViewModel.kt    is Loading -> LoadingScreen()

│   │   │   │   ├── channels/ChannelListViewModel.kt    is Success -> ContentScreen(data)

│   │   │   │   ├── messages/MessageListViewModel.kt    is Error -> ErrorScreen(message)

│   │   │   │   └── ViewModelFactory.kt}

│   │   │   ├── ChatApplication.kt```

│   │   │   ├── MainActivity.kt

│   │   │   ├── LoginActivity.kt## 🚧 Troubleshooting

│   │   │   ├── ChannelListActivity.kt

│   │   │   └── MessageListActivity.kt### Build Errors

│   │   ├── res/**Problem**: "Unsupported class file major version 67"

│   │   │   ├── layout/              # XML layouts (toolbar containers)**Solution**: Ensure JDK 17 is set in Android Studio:

│   │   │   ├── menu/                # Toolbar menus1. File → Project Structure → SDK Location

│   │   │   ├── values/              # Strings, colors, themes2. Set JDK to version 17

│   │   │   └── drawable/            # Icons

│   │   └── AndroidManifest.xml### Connection Issues

│   ├── build.gradle.kts             # App dependencies**Problem**: "Unable to connect to Stream Chat"

│   └── google-services.json         # Firebase config**Solution**: Check internet connection and API key validity

├── backend/token-server/

│   ├── index.js                     # Express server### Gradle Sync Failed

│   ├── package.json**Solution**: 

│   ├── .env                         # Environment variables1. File → Invalidate Caches → Invalidate and Restart

│   └── README.md2. Clean project: Build → Clean Project

├── gradle/libs.versions.toml        # Dependency versions3. Rebuild: Build → Rebuild Project

├── build.gradle.kts

├── settings.gradle.kts## 📚 Documentation

└── README.md                        # This file

```- [Stream Chat Android Documentation](https://getstream.io/chat/docs/android/?language=kotlin)

- [Jetpack Compose Guide](https://developer.android.com/jetpack/compose)

---- [MVVM Architecture](https://developer.android.com/topic/architecture)



## 🐛 Troubleshooting## 🔮 Future Enhancements



### Backend Issues### Planned Features

- [ ] Unit tests for ViewModels

**Port 8080 already in use**- [ ] Create channel screen

```powershell- [ ] User profile management

netstat -ano | findstr :8080- [ ] Push notifications

taskkill /PID <PID> /F- [ ] Thread replies

```- [ ] Message search

- [ ] File attachments (PDF, docs)

**Firebase auth fails**- [ ] Voice messages

- Verify `GOOGLE_APPLICATION_CREDENTIALS` path in `.env`- [ ] Giphy integration

- Ensure service account has "Firebase Authentication Admin" role

## 📄 License

### Android Issues

This project is for educational purposes.

**401 Unauthorized from /token**

- Check Firebase user is signed in## 👨‍💻 Developer

- Verify backend `.env` has correct Firebase credentials

- Check Android app has `INTERNET` permission in manifestBuilt with ❤️ using Stream Chat SDK and Jetpack Compose



**429 Rate Limit Exceeded**---

- Debounce interval may be too short (increase from 1000ms to 2000ms)

- Check if multiple instances are polling excessively## 🎓 Learning Resources



**Build errors after adding group features**### MVVM Pattern

- Ensure coroutine imports: `kotlinx.coroutines.launch`, `androidx.compose.runtime.rememberCoroutineScope`- Single source of truth (Repository)

- Verify Stream SDK version is 6.0.13 (check `gradle/libs.versions.toml`)- Reactive state management (StateFlow)

- Lifecycle-aware components (ViewModel)

---- Separation of concerns (View/ViewModel/Model)



## 📝 Development Notes### Stream Chat SDK

- Real-time messaging

### Code Quality- Offline support

- **No redundant code**: Supabase implementation removed, only Firebase auth remains- State management plugins

- **MVVM separation**: Clear boundaries between data/domain/presentation layers- Compose UI components

- **Compose-first**: All UI in Jetpack Compose (no XML layouts for content)

- **Kotlin coroutines**: Structured concurrency, no ExecutorService or callbacks### Jetpack Compose

- Declarative UI

### Testing- State hoisting

Run unit tests:- Recomposition

```bash- Material Design 3

./gradlew test

```---



Run instrumented tests:**Note**: This is a production-ready app with clean architecture, proper state management, and modern Android development practices.

```bash
./gradlew connectedAndroidTest
```

### Build Variants
```bash
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK (requires signing config)
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is open source and available under the MIT License.

---

## 🔗 Resources

- [Stream Chat Android SDK Docs](https://getstream.io/chat/docs/sdk/android/)
- [Firebase Authentication Docs](https://firebase.google.com/docs/auth/android/start)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [MVVM Architecture Guide](https://developer.android.com/topic/architecture)

---

## 📧 Support

For issues or questions:
- Open a GitHub issue
- Check Stream Chat [community forum](https://github.com/GetStream/stream-chat-android/discussions)
- Firebase support: [firebase.google.com/support](https://firebase.google.com/support)

---

**Built with ❤️ using Stream Chat SDK, Firebase, and Jetpack Compose**
