# Aria — accounts, subscription & admin (Supabase + Google Play)

How it fits together:

- **Login/signup** (email + password) is handled by **Supabase**.
- **Payment** is handled by **Google Play Billing**.
- The **"is subscribed" flag lives on the Supabase account** (`profiles.plan`),
  not the Google account. So a user pays once via Google Play, and from then on
  **logging in with their email = subscribed**, on any device, after any
  reinstall.
- The **admin panel** (a single web page) shows your users, their plan, and
  their conversations.

## 1. Create the Supabase project
supabase.com → New project. Copy the **Project URL** and **anon key**
(Settings → API).

## 2. Create the database
SQL editor → paste **`schema.sql`** → Run. (profiles, messages, plan, row-level
security, signup trigger.)

## 3. Make signup instant (optional, easier for testing)
Auth → Providers → Email → turn **off** "Confirm email" so a new account is
logged in immediately. (Leave it on if you want email verification.)

## 4. Point the app at Supabase + Play
In the app's **`local.properties`** (gitignored):

```properties
ANTHROPIC_API_KEY=sk-ant-...          # the model key (still in the app for now)
ANTHROPIC_MODEL=claude-haiku-4-5
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=YOUR-ANON-KEY
BILLING_ENABLED=true                  # false while testing without payments
SUB_PRODUCT_ID=aria_premium           # your Play subscription product id
```

When `SUPABASE_URL` is set, the app shows a **login screen** first. When
`BILLING_ENABLED=true`, it shows a **Subscribe** paywall until the account's
plan is `pro`. After a successful Google Play purchase, the app sets that
account's plan to `pro` — so it follows the login from then on.

## 5. Become admin + open the panel
- SQL editor: `update public.profiles set is_admin = true where email = 'you@example.com';`
- Edit `admin/index.html` (top): set `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
- Open `admin/index.html`, sign in with your admin account → see users, their
  plan, message counts, and each conversation.

## Where's the money view?
**Revenue/subscriber numbers are in the Google Play Console** (Play handles the
billing). The admin panel here is for **accounts + chats + plan**.

## ⚠️ Honest limitations
- **Entitlement is set by the app after a Play purchase** (not yet verified
  server-side), so a determined user could fake "pro". Fine to launch small;
  to harden, add a Supabase Edge Function that verifies the purchase token with
  the Google Play Developer API before setting `plan = 'pro'`. (I can add this.)
- **Privacy:** the admin reads users' messages. You must disclose this and get
  consent (the login screen shows a short consent line — add a full privacy
  policy before launch).
