# BuzzOff: Play Console answer sheet

Answers for **Play Console › BuzzOff › Test and release › App content** (also shown
as "Set up your app" on the dashboard). Facts behind them: BuzzOff has no login,
no ads, no internet permission, no analytics or third-party SDKs, and stores
everything on the device.

---

## Create app (first screen)

| Question | Answer |
|---|---|
| App name | BuzzOff |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | Free |
| Declarations | Tick both (Developer Program Policies, US export laws) |

---

## Privacy policy

```
https://vastvik123.github.io/BuzzOff/privacy-policy.html
```

## App access

**All functionality in my app is available without any access restrictions.**
(The PIN is created by the user in the app; there is no login.)

## Ads

**No, my app does not contain ads.**

## Content rating

- Email: `dopplertechnologies98@gmail.com`
- Category: **All Other App Types**
- Answer **No** to every question (violence, sexuality, language, controlled
  substances, gambling, user-generated content, sharing location, digital purchases,
  unrestricted internet access).
- Expected result: **Everyone / PEGI 3 / IARC 3+**.

## Target audience and content

- Target age groups: **13–15, 16–17, 18 and over** (do **not** tick under-13 ages;
  that would bring in the Families policy).
- Could the app unintentionally appeal to children? **No**.

## News app

**No.**

## Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |

Play then asks nothing further about data types. If it asks about security
practices: data is not transmitted, so encryption in transit does not apply; users
delete data by deleting alarms or uninstalling the app.

## Advertising ID

**No**, the app does not use an advertising ID.

## Government apps

**No.**

## Financial features

**My app doesn't provide any financial features.**

## Health apps

**No** health features.

---

## Permission declarations

Play asks for these when you upload the first release (under
**App content › Sensitive app permissions** or during release review).

### Exact alarm (`USE_EXACT_ALARM`)

- Use case: **Alarm clock**, the app's core functionality.
- Description:

```
BuzzOff is an alarm clock. Users set alarms that must ring at the exact minute chosen, including repeating alarms and snoozes. USE_EXACT_ALARM is used only to schedule these user-set alarms with AlarmManager.setAlarmClock().
```

### Full-screen intent (`USE_FULL_SCREEN_INTENT`)

- Use case: **Alarm clock** (alarms set by the user).
- Description:

```
When a user-set alarm goes off, BuzzOff shows a full-screen ringing screen over the lock screen so the user can snooze, dismiss, or use "I'm up" to silence the rest of that category's alarms.
```

### Foreground service (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`)

- Foreground service type: **Media playback**
- Task / description:

```
When a user-set alarm rings, BuzzOff runs a foreground service that plays the alarm sound (and vibration) until the user snoozes or dismisses it, or until the user-chosen auto-stop time. The ongoing notification lets the user snooze or dismiss.
```

- Impact if the task is deferred or interrupted:

```
The alarm would stop ringing or never start, so the user could oversleep.
```

- User-initiated? **Yes**, it runs only for alarms the user created.
- **Video link:** a short screen recording of an alarm ringing (Settings › Ring a
  test alarm now → ringing screen → Dismiss). Upload it to YouTube as **Unlisted**
  or to Google Drive with "Anyone with the link" and paste the link.

---

## Releases

### Internal testing (first upload)

- Upload: `app/build/outputs/bundle/release/app-release.aab`
- Play App Signing: **accept** (Google keeps the app signing key; your
  `buzzoff-upload.jks` stays the upload key).
- Release name: `1.0 (1)`
- Release notes:

```
<en-US>
First release of BuzzOff: group your alarms into categories and silence the rest of a category with one PIN.
</en-US>
```

### Closed testing

- Create a track (e.g. "Closed testers"), add an email list with your 12+ testers.
- Countries: your own (e.g. India), or all.
- Promote the same release from internal testing.
- Share the opt-in link with testers; they must **stay opted in for 14 days**.

### Production

After 14 days: **Dashboard › Apply for production**, answer the questions about
your test, then roll out once approved.
