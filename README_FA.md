# 🟠 RKh BPB Wizard — راهنمای فارسی

<p align="center">
  <strong>ابزار لوکال، سبک و بدون وابستگی برای ساخت BPB Worker + KV روی Cloudflare</strong>
</p>

<p align="center">
  <a href="https://t.me/pingplas_channel"><strong>📣 Telegram: @pingplas_channel</strong></a>
</p>

---

## ✨ معرفی

**RKh BPB Wizard** یک وب‌اپ لوکال برای ساخت، مدیریت و حذف Worker و KV در Cloudflare است. برنامه روی سیستم شما اجرا می‌شود، فایل Worker داخل پروژه را Deploy می‌کند، مقدارهای لازم مثل `UUID`، `TR_PASS`، `Panel Password` و `SUB_PATH` را به‌صورت رندوم می‌سازد و نتیجه را در یک  نمایش می‌دهد.

این نسخه برای اجرا به `pip`، `venv`، `uvicorn`، `FastAPI` یا هیچ dependency خارجی نیاز ندارد.

---

## 🚀 امکانات اصلی

- 👤 ساخت و مدیریت چند پروفایل Cloudflare
- 🔐 پشتیبانی از دو روش ورود:
  - API Token
  - Global API Key + Email
- ☁️ ساخت Cloudflare Worker از داخل پنل
- 🗃️ ساخت KV Namespace و اتصال آن به Worker با binding دقیق `kv`
- 🎲 ساخت رندوم مقدارهای لازم در هر Deploy:
  - `UUID`
  - `TR_PASS`
  - `Panel Password`
  - `SUB_PATH`
- 🧠 پیشنهاد نام رندوم برای Worker و KV
- 🚫 نام‌های پیشنهادی از واژه‌های `bpb` و `worker` استفاده نمی‌کنند
- 📊 صفحه Result بعد از Deploy با لینک‌ها و رمزهای مهم
- 🔗 نمایش این موارد در History:
  - Panel URL
  - Panel Password
  - Subscription URL
  - Worker URL
- 🧹 حذف Worker + KV از Cloudflare با یک دکمه
- ✅ حذف رکورد از Deploy History بعد از Cleanup موفق
- 🧽 دکمه Reset Local Data برای پاک‌کردن اطلاعات محلی
- 🌐 پشتیبانی از VPN سیستم و HTTP/HTTPS Proxy اختیاری
- 🧩 اجرای مستقیم با Python خام

---

## 📁 ساختار پروژه

```text
rkh_bpb_wizard_v1_10/
├─ rkh_bpb_wizard.py          # فایل اصلی برنامه
├─ run.bat                    # اجرای سریع در Windows
├─ run.sh                     # اجرای سریع در Linux/macOS
├─ rkh_bpb_profiles.json      # پروفایل‌ها و Deploy History محلی
├─ static/
│  └─ index.html              # رابط کاربری
├─ worker/
│  └─ worker.js               # Worker آماده Deploy
├─ README.md                  # صفحه انتخاب زبان
├─ README_FA.md               # راهنمای فارسی
└─ README_EN.md               # راهنمای انگلیسی
```

---

## ✅ پیش‌نیازها

فقط این‌ها لازم است:

- 🐍 Python نصب باشد
- ☁️ یک Cloudflare Account داشته باشی
- 🔑 API Token یا Global API Key + Email داشته باشی
- 🌐 اگر Cloudflare API در شبکه تو باز نیست، VPN سیستم یا HTTP/HTTPS Proxy داشته باشی

> پیشنهاد بهتر: از **API Token محدود** استفاده کن، نه Global API Key عمومی.

---

## 🪟 اجرا در Windows

پوشه ZIP را Extract کن، وارد پوشه پروژه شو و اجرا کن:

```powershell
.\run.bat
```

یا دستی:

```powershell
python rkh_bpb_wizard.py
```

بعد از اجرا، داخل CMD یک لینک با `token=` چاپ می‌شود. همان لینک را در مرورگر باز کن:

```text
http://127.0.0.1:8000/?token=...
```

---

## 🐧 اجرا در Linux / macOS

```bash
chmod +x run.sh
./run.sh
```

یا دستی:

```bash
python3 rkh_bpb_wizard.py
```

سپس لینکی که در Terminal چاپ می‌شود را باز کن.

---

## 🧭 راهنمای استفاده مرحله‌به‌مرحله

1. 🚀 برنامه را اجرا کن.
2. 🔗 لینک token دار داخل CMD/Terminal را باز کن.
3. 👤 یک پروفایل Cloudflare بساز.
4. 🔐 نوع احراز هویت را انتخاب کن:
   - API Token
   - Global API Key + Email
5. 💾 پروفایل را ذخیره کن.
6. ☁️ اکانت Cloudflare را Load کن.
7. 🎲 نام Worker و KV پیشنهادی را تأیید کن یا نام جدید بساز.
8. ⚡ روی Deploy بزن.
9. 📊 در صفحه Result، لینک‌ها و رمزها را ذخیره کن.
10. 🏠 با Home به صفحه اصلی برگرد.
11. 🧹 برای حذف کامل، از دکمه Delete Worker + KV استفاده کن.

---

## 🗂️ داده‌های محلی

اطلاعات پروفایل‌ها و Deploy History داخل فایل زیر ذخیره می‌شود:

```text
rkh_bpb_profiles.json
```

محتوای اولیه فایل خالی است:

```json
{
  "profiles": [],
  "deployments": []
}
```

اگر خواستی همه اطلاعات محلی پاک شود، از دکمه **Reset Local Data** داخل برنامه استفاده کن.

---

## 🔐 نکات امنیتی مهم

- برنامه روی سیستم خودت اجرا می‌شود.
- اطلاعات پروفایل در فایل local ذخیره می‌شود.
- اگر credential ذخیره کردی، پوشه پروژه را برای دیگران نفرست.
- بهتر است برای Cloudflare یک API Token محدود و مخصوص Workers/KV بسازی.
- Global API Key پشتیبانی می‌شود، اما معمولاً دسترسی گسترده‌تری دارد.
- بعد از Deploy، `Panel Password` و لینک‌ها را در جای امن ذخیره کن.

---

## 🌐 VPN و Proxy

اگر Cloudflare API در منطقه یا شبکه تو محدود باشد:

- اول VPN سیستم را روشن کن.
- اگر برنامه هنوز وصل نشد، داخل پروفایل یک HTTP/HTTPS Proxy وارد کن.

نمونه:

```text
http://127.0.0.1:7890
http://127.0.0.1:8080
```

---

## 🧯 رفع خطاهای رایج

### ❌ صفحه باز می‌شود ولی عملیات انجام نمی‌شود

لینک token دار چاپ‌شده در CMD/Terminal را باز کن، نه فقط آدرس خام زیر:

```text
http://127.0.0.1:8000/
```

### ❌ Cloudflare API خطا می‌دهد

VPN سیستم را روشن کن یا HTTP/HTTPS Proxy در پروفایل وارد کن.

### ❌ پروفایل قبلی هنوز دیده می‌شود

روی **Reset Local Data** بزن، سپس صفحه را Refresh کن.

### ❌ پورت 8000 اشغال است

برنامه‌ای که پورت 8000 را گرفته ببند، یا با پورت دیگر اجرا کن:

```powershell
set RKH_BPB_PORT=8010
python rkh_bpb_wizard.py
```

در Linux/macOS:

```bash
RKH_BPB_PORT=8010 python3 rkh_bpb_wizard.py
```

---

## 🧹 حذف کامل Deploy

با دکمه **Delete Worker + KV** برنامه این کارها را انجام می‌دهد:

- Worker ساخته‌شده را از Cloudflare حذف می‌کند
- KV Namespace مربوط به همان Deploy را حذف می‌کند
- رکورد همان Deploy را از Deploy History محلی پاک می‌کند

---

## ⚠️ نکته نهایی

قبل از Deploy، فایل `worker/worker.js` را بررسی کن و مطمئن شو مجوزهای Cloudflare که استفاده می‌کنی مناسب هستند.

---

## 📣 ارتباط

Telegram: [@pingplas_channel](https://t.me/pingplas_channel)
