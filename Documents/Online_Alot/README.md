# Cutify

## App Usage Guide

This app has 3 roles:
- **User** (customer)
- **Admin** (barber)
- **Super Admin** (manager/owner)

## 1) Landing Page / Start

1. Open the app.
2. On the landing screen, tap **Continue**.
3. You will be taken to the `Sign In` page.

## 2) User Flow (Customer)

1. On the `Sign In` screen, tap **Get Started**.
2. On the Home screen, you will see the list of barbers.
   - **Active** = on duty
   - **Inactive** = not on duty, but the account still exists
3. Select a barber, then tap **Book**.
4. You can check the queue using the queue button on each barber card.
5. The queue uses **FIFO (First In, First Out)**:
   - the first customer to book/walk in is served first.

## 3) Admin Flow (Barber)

1. On `Sign In`, enter admin email and password.
2. After a successful sign-in:
   - your status becomes `on duty`
   - your queue appears in `Admin Main`
3. Admin actions:
   - add **Walk-in**
   - mark a queue item as **Done**
   - **Cancel** a queue item
4. If the admin logs out:
   - the barber card on the user side becomes **inactive**
   - the card remains visible unless deleted by Super Admin.

## 4) Super Admin Flow

1. Sign in as Super Admin.
2. Manage staff/admin accounts:
   - invite a new admin
   - delete an admin account
3. If an admin account is deleted:
   - the barber card is removed from the user side
   - it will not return unless a valid account is created again.

## 5) Important Barber Card Rules

- A new admin card does not appear until the admin successfully signs in.
- Once signed in, the card appears.
- If the admin only logs out, the card stays but becomes inactive.
- A card is permanently removed only when the account is deleted by Super Admin.

## Technical Notes

- Landing layout: `app/src/main/res/layout/activity_main.xml`
- Continue button id: `btn_continue_user`
- User home screen: `app/src/main/java/com/example/online_alot/HomeActivity.java`
- Queue logic: `app/src/main/java/com/example/online_alot/QueueManager.java`
