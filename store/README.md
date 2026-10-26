# Play Store listing

Graphics for OAF's store listing, matching Play Console's **Main store listing → Graphics**:

| File or folder | Play Console field | Size |
|---|---|---|
| `icon-512.png` | App icon | 512 × 512 (Play rounds the corners) |
| `feature-graphic.png` | Feature graphic | 1024 × 500 |
| `phone/` | Phone screenshots | 1440 × 2560 |
| `tablet-7/` | 7-inch tablet screenshots | 1440 × 2560 |
| `tablet-10/` | 10-inch tablet screenshots | 2560 × 1440 |
| `chromebook/` | Chromebook screenshots | 2560 × 1440 |

Screenshots are uploaded in file-name order. The screens inside the frames are drawn by the app's
own code (`ScreensTest`, which also renders the tablet sizes into `app/build/screens/`).

## Videos

- `videos/OAF MANAGE_EXTERNAL_STORAGE.mp4`: the All files access declaration (MANAGE_EXTERNAL_STORAGE).
- `videos/OAF QUERY_ALL_PACKAGES.mp4`: the QUERY_ALL_PACKAGES declaration.
- `videos/OAF REQUEST_INSTALL_PACKAGES.mp4`: the REQUEST_INSTALL_PACKAGES declaration.
- `videos/OAF FOREGROUND_SERVICE_MEDIA_PLAYBACK.mp4`: the media playback foreground service declaration.

Upload them to YouTube as unlisted and paste the links into the declarations (App content → Permissions).
