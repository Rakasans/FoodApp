-- Przepisarnia: schemat synchronizacji (lustro tabel Room z aplikacji).
-- Zasady:
--  * id = UUID generowany na telefonie (bez kolizji przy synchronizacji),
--  * owner_id = właściciel (auth.users); każdy widzi i zmienia WYŁĄCZNIE swoje wiersze (RLS),
--  * created_at / updated_at / deleted_at = czas z telefonu w ms (jak w Room; deleted_at = miękkie usuwanie),
--  * server_updated_at = czas zapisu na serwerze — po nim telefon pobiera zmiany („od ostatniej synchronizacji”),
--  * nieznane wartości zostają NULL (zasada wiarygodności danych — nigdy 0 w miejsce braku).

create extension if not exists moddatetime with schema extensions;

-- ---------------------------------------------------------------- przepisy
create table public.recipe (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  status text not null check (status in ('MATERIAL', 'DRAFT', 'APPROVED')),
  processing text not null default 'NONE',
  name text not null default '',
  servings integer check (servings is null or servings > 0),
  yield_text text,
  prep_time text,
  cook_time text,
  notes text,
  photo_path text,               -- ścieżka w Storage (bucket photos), nie na telefonie
  source_type text not null default 'MANUAL',
  source_url text,
  source_author text,
  material_kind text not null default 'NONE',
  material_text text,
  material_path text,            -- zdjęcie materiału (OCR) w Storage
  material_note text,
  nutrition_origin text not null default 'NONE',
  nutrition_basis text,
  allergens_origin text not null default 'NONE',
  allergens_text text,
  favorite boolean not null default false,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);
create index recipe_owner_sync_idx on public.recipe (owner_id, server_updated_at);

create table public.ingredient (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  recipe_id uuid not null references public.recipe (id) on delete cascade,
  position integer not null,
  group_name text,
  raw_text text not null,        -- oryginalny zapis — zawsze zachowany
  name text not null,
  quantity double precision,     -- NULL = brak danych (nie 0)
  quantity_max double precision, -- górna granica zakresu
  unit text,
  no_quantity boolean not null default false,
  unit_inferred boolean not null default false,
  server_updated_at timestamptz not null default now()
);
create index ingredient_recipe_idx on public.ingredient (recipe_id);
create index ingredient_owner_sync_idx on public.ingredient (owner_id, server_updated_at);

create table public.step (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  recipe_id uuid not null references public.recipe (id) on delete cascade,
  position integer not null,
  text text not null,
  server_updated_at timestamptz not null default now()
);
create index step_recipe_idx on public.step (recipe_id);
create index step_owner_sync_idx on public.step (owner_id, server_updated_at);

create table public.nutrition_value (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  recipe_id uuid not null references public.recipe (id) on delete cascade,
  position integer not null,
  label text not null,
  value_text text not null,
  server_updated_at timestamptz not null default now()
);
create index nutrition_recipe_idx on public.nutrition_value (recipe_id);

-- ---------------------------------------------------------------- kategorie, etykiety, kolekcje
create table public.category (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  is_default boolean not null default false,
  position integer not null default 0,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

create table public.tag (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

create table public.collection (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

create table public.recipe_category (
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  recipe_id uuid not null references public.recipe (id) on delete cascade,
  category_id uuid not null references public.category (id) on delete cascade,
  server_updated_at timestamptz not null default now(),
  primary key (recipe_id, category_id)
);

create table public.recipe_tag (
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  recipe_id uuid not null references public.recipe (id) on delete cascade,
  tag_id uuid not null references public.tag (id) on delete cascade,
  server_updated_at timestamptz not null default now(),
  primary key (recipe_id, tag_id)
);

create table public.recipe_collection (
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  recipe_id uuid not null references public.recipe (id) on delete cascade,
  collection_id uuid not null references public.collection (id) on delete cascade,
  server_updated_at timestamptz not null default now(),
  primary key (recipe_id, collection_id)
);

-- ---------------------------------------------------------------- lista zakupów
create table public.shopping_part (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  quantity double precision,
  quantity_max double precision,
  unit text,
  no_quantity boolean not null default false,
  source_recipe_id uuid,         -- bez klucza obcego: przepis mógł zostać usunięty
  source_recipe_name text,
  raw_text text,
  factor_label text,
  manual boolean not null default false,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  server_updated_at timestamptz not null default now()
);

create table public.shopping_item_state (
  owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  normalized_name text not null,
  checked boolean not null default false,
  quantity_override text,
  updated_at bigint not null,
  server_updated_at timestamptz not null default now(),
  primary key (owner_id, normalized_name)
);

-- ---------------------------------------------------------------- server_updated_at przy każdej zmianie
do $$
declare t text;
begin
  foreach t in array array[
    'recipe', 'ingredient', 'step', 'nutrition_value', 'category', 'tag', 'collection',
    'recipe_category', 'recipe_tag', 'recipe_collection', 'shopping_part', 'shopping_item_state'
  ] loop
    execute format(
      'create trigger %I before update on public.%I for each row execute procedure extensions.moddatetime(server_updated_at)',
      t || '_server_updated_at', t
    );
  end loop;
end $$;

-- ---------------------------------------------------------------- reguły dostępu (RLS): tylko własne dane
do $$
declare t text;
begin
  foreach t in array array[
    'recipe', 'ingredient', 'step', 'nutrition_value', 'category', 'tag', 'collection',
    'recipe_category', 'recipe_tag', 'recipe_collection', 'shopping_part', 'shopping_item_state'
  ] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('create policy %I on public.%I for select to authenticated using ((select auth.uid()) = owner_id)', t || '_select_own', t);
    execute format('create policy %I on public.%I for insert to authenticated with check ((select auth.uid()) = owner_id)', t || '_insert_own', t);
    execute format('create policy %I on public.%I for update to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id)', t || '_update_own', t);
    execute format('create policy %I on public.%I for delete to authenticated using ((select auth.uid()) = owner_id)', t || '_delete_own', t);
    execute format('create index if not exists %I on public.%I (owner_id)', t || '_owner_idx', t);
  end loop;
end $$;

-- Wiersz podrzędny nie może wskazywać cudzego przepisu (np. składnik dopięty do przepisu innej osoby).
create or replace function public.ensure_same_owner_recipe() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
  if not exists (select 1 from public.recipe r where r.id = new.recipe_id and r.owner_id = new.owner_id) then
    raise exception 'recipe % does not belong to owner', new.recipe_id using errcode = '42501';
  end if;
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array['ingredient', 'step', 'nutrition_value', 'recipe_category', 'recipe_tag', 'recipe_collection'] loop
    execute format(
      'create trigger %I before insert or update on public.%I for each row execute function public.ensure_same_owner_recipe()',
      t || '_same_owner', t
    );
  end loop;
end $$;

-- ---------------------------------------------------------------- zdjęcia (Storage): prywatny bucket, folder = id użytkownika
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('photos', 'photos', false, 10485760, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

create policy "photos_select_own" on storage.objects for select to authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "photos_insert_own" on storage.objects for insert to authenticated
  with check (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "photos_update_own" on storage.objects for update to authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "photos_delete_own" on storage.objects for delete to authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
