# NetMaster Learning — Complete Initial 1.0.0

نسخهٔ یکپارچهٔ آموزشی/لابراتواری برای شبکه، سیستم، امنیت و زیرساخت، با محتوای قابل اجرا و شواهد قابل‌راستی‌آزمایی.

## موجودی محتوا
- **95 مسیر آموزشی**
- **950 درس**
- **148 لاب**
- **160 سناریوی Break/Fix**
- **5 کاتالوگ مرجع تخصصی** با **56 مرجع/کارت** که هم در Search و هم در RAG وارد می‌شوند
- **metadata: 1.0.0-complete**؛ پنج فایل reference در assets موجود و در Repository/Search/RAG لود می‌شوند

## مدل هر درس
Concept → Architecture/Flow → Commands → Verification → Troubleshooting → Lab → Quiz → Evidence → Rollback

در سطوح 1–77 متن‌های قالبی بازنویسی شده‌اند تا توضیح فنی، مثال، packet/state view، عیب‌یابی و سنجهٔ پذیرش به خود موضوع درس متکی باشند. سطوح 78–95 نیز لایهٔ specialist با syntax، verification، fault analysis، packet/state walkthrough و سناریوی تولیدی دارند.

## کاتالوگ‌های مرجع
مسیر `app/src/main/assets/references/` شامل این پنج فایل است:
- `specialist_books.json`
- `protocol_catalog.json`
- `commands.json`
- `fault_matrix.json`
- `packet_journeys.json`

محتوای آن‌ها بر پایهٔ استانداردها و مستندات رسمی/عمومی است و متن کتاب‌های دارای حق‌نشر داخل برنامه کپی نشده است.

## عملکرد و جلوگیری از هنگ
- بارگذاری محتوا روی IO/Background انجام می‌شود.
- ساخت RAG index در Background است و درصد پیشرفت + ثانیهٔ سپری‌شده نمایش داده می‌شود.
- PCAP parsing در Background انجام می‌شود.
- AI دارای timeout است تا UI معطل نماند.
- Digital Twin، Packet Analysis، Config Diff و Scenario Engine عملیات اصلی را در حافظه و قابل تست اجرا می‌کنند.

## ورود
این نسخه یک احراز هویت محلی تک‌کاربره دارد. credential در APK به‌صورت PBKDF2 hash نگهداری می‌شود و plaintext password داخل کد قرار نگرفته است. این مدل برای نسخهٔ شخصی/اولیه مناسب است؛ انتشار عمومی به authentication سمت‌سرور و secret/session management نیاز دارد.

## Build محلی
```bash
./gradlew assembleDebug
./gradlew test
```

Windows:
```bat
gradlew.bat assembleDebug
gradlew.bat test
```

## GitHub
Workflow در `.github/workflows/android.yml` قرار دارد و unit test و Debug APK را در CI اجرا می‌کند.

برای Push مستقیم به GitHub باید اتصال GitHub در حساب ChatGPT فعال شود؛ تا پیش از آن فایل نهایی را می‌توان با git محلی push کرد.

## وضعیت اعتبارسنجی
- IDs درس‌ها: 950/950 یکتا
- عنوان درس‌ها: 950/950 یکتا
- دستورهای تکراری داخل هر درس: صفر
- عبارت «نمونه» در فیلد commands: صفر
- packet/state walkthrough: برای همهٔ 950 درس موجود
- reference catalogs: 5/5 موجود و در Repository/Search/RAG مصرف می‌شوند
- expert fields: 950/950 دارای verification، failure analysis، packet/state walkthrough، runbook و reference اختصاصی
- command/evidence alignment: 950/950؛ فرمان اصلی هر درس با verification، runbook و packet/state path همان درس هم‌راستا شده است
- UI long-running work: load/RAG/PCAP دارای progress + elapsed seconds هستند؛ AI دارای timeout است
- legacy fields: deepTechnical/deepDive/configuration_playbook/commands/quiz/questions نیز بازبینی و بدون placeholder ذخیره شده‌اند

دستورات Vendor ممکن است با نسخهٔ محصول تغییر کنند؛ در Lab syntax همان نسخه را با help داخلی و documentation رسمی بررسی کنید.

## محدودیت اعتبارسنجی محیطی
در محیط اجرای فعلی، دانلود Gradle از `services.gradle.org` در دسترس نبود؛ بنابراین `assembleDebug`/`test` کامل Android در همین محیط قابل اجرای نهایی نیست. تست‌های موتورهای دامنه با کامپایل مستقل Kotlin و تست‌های واحد موجود بازبینی شده‌اند و GitHub Actions برای build/test پروژه در CI آماده است.
