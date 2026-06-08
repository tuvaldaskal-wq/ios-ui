# Aria backend (Supabase) — setup

This makes accounts/plans survive a reinstall, keeps your Anthropic key on the
server, logs usage, and powers the admin panel.

## 1. Create a Supabase project
Go to https://supabase.com → New project. Note your **Project URL** and **anon
key** (Settings → API).

## 2. Create the database
SQL editor → paste **`schema.sql`** → Run. This creates `profiles`, `messages`,
`usage`, `payments`, row-level security, and a trigger that makes a profile on
signup.

## 3. Deploy the chat function (holds your API key)
Install the Supabase CLI, then:

```bash
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase secrets set ANTHROPIC_API_KEY=sk-ant-...        # your real key — server only
supabase functions deploy chat --no-verify-jwt
```

The function URL will be:
`https://YOUR_PROJECT.supabase.co/functions/v1/chat`

## 4. Point the app at the backend (no key in the app!)
In the app's **`local.properties`** (gitignored), set:

```properties
# Leave ANTHROPIC_API_KEY EMPTY for the public build — the server holds it.
ANTHROPIC_MODEL=claude-haiku-4-5
BACKEND_URL=https://YOUR_PROJECT.supabase.co/functions/v1/chat
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=YOUR-ANON-KEY
```

When `SUPABASE_URL`/`SUPABASE_ANON_KEY` are set, the app shows a **sign-in
screen** first, and when `BACKEND_URL` is set it routes chat through the
function (sending the user's JWT) — so the key is never in the APK and usage is
tied to the account.

## 5. Make yourself admin + open the panel
- SQL editor: `update public.profiles set is_admin = true where email = 'you@example.com';`
- Edit `admin/index.html` top: set `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
- Open `admin/index.html` (any static host, or just open the file) and sign in
  with your admin account. You'll see revenue, your API cost, per-user spend,
  and each user's conversation.

## 6. Recording revenue (how much each user pays)
Insert a `payments` row from your billing webhook (Stripe / Israeli gateway)
when a user pays, e.g.:

```sql
insert into public.payments (user_id, amount_usd, provider)
values ('<auth-user-id>', 9.99, 'stripe');
```

The admin panel sums these into per-user **Paid** and total **revenue**.

## ⚠️ Privacy / legal
The admin panel shows users' message content. Before going live you **must**
disclose this in a privacy policy and obtain consent (GDPR / Israeli Privacy
Protection Law). The signup screen already shows a short consent line; add a
full policy and, ideally, only retain what you truly need.
