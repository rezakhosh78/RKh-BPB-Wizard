# 🟠 RKh BPB Wizard — English Guide

<p align="center">
  <strong>A  wizard for deploying the bundled BPB Worker + KV to Cloudflare</strong>
</p>

<p align="center">
  <a href="https://t.me/pingplas_channel"><strong>📣 Telegram: @pingplas_channel</strong></a>
</p>

---

## ✨ Overview

**RKh BPB Wizard** is a local web app for creating, managing, and deleting a Cloudflare Worker with a KV namespace. It runs on your own machine, deploys the bundled Worker file, generates the required deployment values automatically, and shows the final result in a dark orange Liquid Glass interface.

This edition runs directly with Python and does not require `pip`, `venv`, `uvicorn`, `FastAPI`, or any external dependency.

---

## 🚀 Features

- 👤 Multiple local Cloudflare profiles
- 🔐 Two authentication modes:
  - API Token
  - Global API Key + Email
- ☁️ Create a Cloudflare Worker from the UI
- 🗃️ Create a KV namespace and bind it to the Worker with the exact binding name `kv`
- 🎲 Random value generation on every deployment:
  - `UUID`
  - `TR_PASS`
  - `Panel Password`
  - `SUB_PATH`
- 🧠 Random Worker/KV name suggestions
- 🚫 Suggested names avoid the words `bpb` and `worker`
- 📊 Result screen after deployment
- 🏠 Home button after the Result screen
- 🧾 Deploy History filtered per selected profile
- 🔗 History shows:
  - Panel URL
  - Panel Password
  - Subscription URL
  - Worker URL
- 🧹 One-click Worker + KV cleanup from Cloudflare
- ✅ Successful cleanup removes the deployment from local history
- 🧽 Reset Local Data button
- 🌐 Works with system-wide VPN and optional HTTP/HTTPS proxy
- 🧩 Direct Python execution

---

## 📁 Project structure

```text
rkh_bpb_wizard_v1_10/
├─ rkh_bpb_wizard.py          # Main application file
├─ run.bat                    # Quick start for Windows
├─ run.sh                     # Quick start for Linux/macOS
├─ rkh_bpb_profiles.json      # Local profiles and deploy history
├─ static/
│  └─ index.html              # Web UI
├─ worker/
│  └─ worker.js               # Bundled Worker file
├─ README.md                  # Language selector
├─ README_FA.md               # Persian guide
└─ README_EN.md               # English guide
```

---

## ✅ Requirements

You only need:

- 🐍 Python installed
- ☁️ A Cloudflare account
- 🔑 An API Token or Global API Key + Email
- 🌐 A system-wide VPN or HTTP/HTTPS proxy if Cloudflare API is blocked on your network

> Recommended: use a restricted **API Token** instead of a broad Global API Key.

---

## 🪟 Run on Windows

Extract the ZIP, open the project folder, then run:

```powershell
.\run.bat
```

Or manually:

```powershell
python rkh_bpb_wizard.py
```

After startup, the CMD window prints a URL containing `token=`. Open that exact URL in your browser:

```text
http://127.0.0.1:8000/?token=...
```

---

## 🐧 Run on Linux / macOS

```bash
chmod +x run.sh
./run.sh
```

Or manually:

```bash
python3 rkh_bpb_wizard.py
```

Then open the tokenized URL printed in the terminal.

---

## 🧭 Step-by-step usage

1. 🚀 Start the app.
2. 🔗 Open the tokenized local URL printed in CMD/Terminal.
3. 👤 Create a Cloudflare profile.
4. 🔐 Choose an authentication mode:
   - API Token
   - Global API Key + Email
5. 💾 Save the profile.
6. ☁️ Load Cloudflare accounts.
7. 🎲 Confirm the suggested Worker/KV names or generate new ones.
8. ⚡ Click Deploy.
9. 📊 Save the links and passwords from the Result screen.
10. 🏠 Use Home to return to the main page.
11. 🧹 Use Delete Worker + KV when you want to remove the deployment.

---

## 🗂️ Local data

Profiles and Deploy History are stored in:

```text
rkh_bpb_profiles.json
```

The included file starts empty:

```json
{
  "profiles": [],
  "deployments": []
}
```

Use **Reset Local Data** in the UI to clear all local profiles and deployment history.

---

## 🔐 Security notes

- The app runs locally on your machine.
- Profile data is stored in a local JSON file.
- Do not share the project folder after saving credentials.
- Prefer a restricted Cloudflare API Token for Workers/KV operations.
- Global API Key is supported, but it usually has broader access.
- Save your `Panel Password` and URLs somewhere safe after deployment.

---

## 🌐 VPN and proxy

If Cloudflare API is blocked in your network or region:

- First try a system-wide VPN.
- If direct access still fails, set an HTTP/HTTPS proxy in the profile.

Examples:

```text
http://127.0.0.1:7890
http://127.0.0.1:8080
```

---

## 🧯 Troubleshooting

### ❌ The page opens, but actions fail

Open the exact tokenized URL printed in CMD/Terminal, not only:

```text
http://127.0.0.1:8000/
```

### ❌ Cloudflare API connection fails

Enable your system-wide VPN or set an HTTP/HTTPS proxy in the profile.

### ❌ Old profile data appears

Click **Reset Local Data**, then refresh the page.

### ❌ Port 8000 is already in use

Close the app using port 8000, or run on another port:

```powershell
set RKH_BPB_PORT=8010
python rkh_bpb_wizard.py
```

On Linux/macOS:

```bash
RKH_BPB_PORT=8010 python3 rkh_bpb_wizard.py
```

---

## 🧹 Full cleanup

The **Delete Worker + KV** action:

- Deletes the deployed Worker from Cloudflare
- Deletes the KV namespace created for that deployment
- Removes that deployment record from the local Deploy History

---

## ⚠️ Final note

Before deployment, review `worker/worker.js` and make sure your Cloudflare permissions are appropriate for your account.

---

## 📣 Contact

Telegram: [@pingplas_channel](https://t.me/pingplas_channel)
