package com.poupa.vinylmusicplayer.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialcab.attached.AttachedCab;
import com.afollestad.materialcab.attached.AttachedCabKt;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.util.DialogUtils;
import com.github.ksoichiro.android.observablescrollview.ObservableListView;
import com.kabouzeid.appthemehelper.util.ColorUtil;
import com.kabouzeid.appthemehelper.util.MaterialValueHelper;
import com.poupa.vinylmusicplayer.R;
import com.poupa.vinylmusicplayer.adapter.album.HorizontalAlbumAdapter;
import com.poupa.vinylmusicplayer.adapter.song.ArtistSongAdapter;
import com.poupa.vinylmusicplayer.databinding.ActivityArtistDetailBinding;
import com.poupa.vinylmusicplayer.databinding.SlidingMusicPanelLayoutBinding;
import com.poupa.vinylmusicplayer.dialogs.AddToPlaylistDialog;
import com.poupa.vinylmusicplayer.dialogs.SleepTimerDialog;
import com.poupa.vinylmusicplayer.glide.GlideApp;
import com.poupa.vinylmusicplayer.glide.VinylColoredTarget;
import com.poupa.vinylmusicplayer.glide.VinylGlideExtension;
import com.poupa.vinylmusicplayer.helper.MusicPlayerRemote;
import com.poupa.vinylmusicplayer.helper.menu.MenuHelper;
import com.poupa.vinylmusicplayer.interfaces.CabCallbacks;
import com.poupa.vinylmusicplayer.interfaces.CabHolder;
import com.poupa.vinylmusicplayer.interfaces.LoaderIds;
import com.poupa.vinylmusicplayer.interfaces.PaletteColorHolder;
import com.poupa.vinylmusicplayer.lastfm.rest.LastFMRestClient;
import com.poupa.vinylmusicplayer.lastfm.rest.model.LastFmArtist;
import com.poupa.vinylmusicplayer.loader.ArtistLoader;
import com.poupa.vinylmusicplayer.misc.SimpleObservableScrollViewCallbacks;
import com.poupa.vinylmusicplayer.misc.WrappedAsyncTaskLoader;
import com.poupa.vinylmusicplayer.model.Artist;
import com.poupa.vinylmusicplayer.model.Song;
import com.poupa.vinylmusicplayer.ui.activities.base.AbsSlidingMusicPanelActivity;
import com.poupa.vinylmusicplayer.util.CustomArtistImageUtil;
import com.poupa.vinylmusicplayer.util.ImageTheme.ThemeStyleUtil;
import com.poupa.vinylmusicplayer.util.MusicUtil;
import com.poupa.vinylmusicplayer.util.NavigationUtil;
import com.poupa.vinylmusicplayer.util.PreferenceUtil;

import java.util.ArrayList;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Be careful when changing things in this Activity!
 */
public class ArtistDetailActivity
        extends AbsSlidingMusicPanelActivity
        implements PaletteColorHolder, CabHolder, LoaderManager.LoaderCallbacks<Artist> {

    private static final int LOADER_ID = LoaderIds.ARTIST_DETAIL_ACTIVITY;
    private static final int REQUEST_CODE_SELECT_IMAGE = 1000;

    public static final String EXTRA_ARTIST_ID = "extra_artist_id";

    private ObservableListView songListView;
    private com.google.android.material.card.MaterialCardView artistBorderTheme;
    private ImageView artistImage;
    private Toolbar toolbar;
    private View headerView;
    private View headerOverlay;

    private ImageView durationIconImageView;
    private ImageView songCountIconImageView;
    private ImageView albumCountIconImageView;
    private TextView durationTextView;
    private TextView songCountTextView;
    private TextView albumCountTextView;
    private TextView titleTextView;

    private View songListHeader;
    private RecyclerView albumRecyclerView;

    private AttachedCab cab;
    private int headerViewHeight;
    private int toolbarColor;

    private Artist artist;
    @Nullable
    private Spanned biography;
    private MaterialDialog biographyDialog;
    private HorizontalAlbumAdapter albumAdapter;
    private ArtistSongAdapter songAdapter;

    private LastFMRestClient lastFMRestClient;

    private boolean forceDownload;
    private final SimpleObservableScrollViewCallbacks observableScrollViewCallbacks = new SimpleObservableScrollViewCallbacks() {
        @Override
        public void onScrollChanged(int scrollY, boolean firstScroll, boolean dragging) {
            final int y = scrollY + headerViewHeight;

            // Change alpha of overlay
            final float headerAlpha = Math.max(0, Math.min(1, (float) 2 * y / headerViewHeight));
            headerOverlay.setBackgroundColor(ColorUtil.withAlpha(toolbarColor, headerAlpha));

            // Translate name text
            headerView.setTranslationY(Math.max(-y, -headerViewHeight));
            headerOverlay.setTranslationY(Math.max(-y, -headerViewHeight));
            artistBorderTheme.setTranslationY(Math.max(-y, -headerViewHeight));
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawUnderStatusbar();

        lastFMRestClient = new LastFMRestClient(this);
        usePalette = PreferenceUtil.getInstance().albumArtistColoredFooters();

        initViews();
        setUpObservableListViewParams();
        setUpToolbar();
        setUpViews();

        LoaderManager.getInstance(this).initLoader(LOADER_ID, getIntent().getExtras(), this);
    }

    @Override
    protected View createContentView() {
        final SlidingMusicPanelLayoutBinding slidingPanelBinding = createSlidingMusicPanel();
        final ActivityArtistDetailBinding binding = ActivityArtistDetailBinding.inflate(
                getLayoutInflater(),
                slidingPanelBinding.contentContainer,
                true);

        songListView = binding.list;
        artistBorderTheme = binding.imageBorderTheme;
        artistImage = binding.image;
        toolbar = binding.toolbar;
        headerView = binding.header;
        headerOverlay = binding.headerOverlay;

        durationIconImageView = binding.durationIcon;
        songCountIconImageView = binding.songCountIcon;
        albumCountIconImageView = binding.albumCountIcon;
        durationTextView = binding.durationText;
        songCountTextView = binding.songCountText;
        albumCountTextView = binding.albumCountText;
        titleTextView = binding.title;

        return slidingPanelBinding.getRoot();
    }

    private boolean usePalette;

    private void setUpObservableListViewParams() {
        headerViewHeight = getResources().getDimensionPixelSize(R.dimen.detail_header_height);
    }

    private void initViews() {
        songListHeader = LayoutInflater.from(this).inflate(R.layout.artist_detail_header, songListView, false);
        albumRecyclerView = songListHeader.findViewById(R.id.recycler_view);
    }

    private void setUpViews() {
        setUpSongListView();
        setUpAlbumRecyclerView();
        setColors(DialogUtils.resolveColor(this, R.attr.defaultFooterColor));
    }

    private void setUpSongListView() {
        setUpSongListPadding();
        songListView.setScrollViewCallbacks(observableScrollViewCallbacks);
        songListView.addHeaderView(songListHeader);

        songAdapter = new ArtistSongAdapter(this, getArtist().getSongs(), this);
        songListView.setAdapter(songAdapter);

        final View contentView = getWindow().getDecorView().findViewById(android.R.id.content);
        contentView.post(() -> observableScrollViewCallbacks.onScrollChanged(-headerViewHeight, false, false));
    }

    private void setUpSongListPadding() {
        songListView.setPadding(0, headerViewHeight, 0, 0);
    }

    private void setUpAlbumRecyclerView() {
        albumRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        albumAdapter = new HorizontalAlbumAdapter(this, getArtist().albums, usePalette, this);
        albumRecyclerView.setAdapter(albumAdapter);
        albumAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                if (albumAdapter.getItemCount() == 0) {finish();}
            }
        });
    }

    private void setUsePalette(boolean usePalette) {
        albumAdapter.usePalette(usePalette);
        PreferenceUtil.getInstance().setAlbumArtistColoredFooters(usePalette);
        this.usePalette = usePalette;
    }

    @Override
    protected void reload() {
        LoaderManager.getInstance(this).restartLoader(LOADER_ID, getIntent().getExtras(), this);
    }

    private void loadBiography() {
        loadBiography(Locale.getDefault().getLanguage());
    }

    private void loadBiography(@Nullable final String lang) {
        biography = null;

        lastFMRestClient.getApiService()
                .getArtistInfo(getArtist().getName(), lang, null)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull final Call<LastFmArtist> call, @NonNull final Response<LastFmArtist> response) {
                        final LastFmArtist lastFmArtist = response.body();
                        if (lastFmArtist != null && lastFmArtist.getArtist() != null && lastFmArtist.getArtist().getBio() != null) {
                            final String bioContent = lastFmArtist.getArtist().getBio().getContent();
                            if (bioContent != null && !bioContent.trim().isEmpty()) {
                                biography = Html.fromHtml(bioContent);
                            }
                        }

                        // If the "lang" parameter is set and no biography is given, retry with default language
                        if (biography == null && lang != null) {
                            loadBiography(null);
                            return;
                        }

                        if (!PreferenceUtil.isAllowedToDownloadMetadata(ArtistDetailActivity.this)) {
                            if (biography != null) {
                                biographyDialog.setContent(biography);
                            } else {
                                biographyDialog.dismiss();
                                Toast.makeText(ArtistDetailActivity.this, getResources().getString(R.string.biography_unavailable), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull final Call<LastFmArtist> call, @NonNull final Throwable t) {
                        t.printStackTrace();
                        biography = null;
                    }
                });
    }

    private void loadArtistImage() {
        GlideApp.with(this)
                .asBitmapPalette()
                .load(VinylGlideExtension.getArtistModel(artist, forceDownload))
                .transition(VinylGlideExtension.getDefaultTransition())
                .artistOptions(artist)
                .dontAnimate()
                .into(new VinylColoredTarget(artistImage) {
                    @Override
                    public void onColorReady(int color) {
                        setColors(color);
                    }
                });
        forceDownload = false;
        artistBorderTheme.setRadius(ThemeStyleUtil.getInstance().getAlbumRadiusImage(ArtistDetailActivity.this));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_IMAGE) {
            if (resultCode == RESULT_OK) {
                CustomArtistImageUtil.getInstance(this).setCustomArtistImage(artist, data.getData());
            }
        } else {
            if (resultCode == RESULT_OK) {
                reload();
            }
        }
    }

    @Override
    public int getPaletteColor() {
        return toolbarColor;
    }

    private void setColors(int color) {
        toolbarColor = color;
        headerView.setBackgroundColor(color);

        setNavigationbarColor(color);
        setTaskDescriptionColor(color);

        toolbar.setBackgroundColor(color);
        setSupportActionBar(toolbar); // needed to auto readjust the toolbar content color
        setStatusbarColor(color);

        final int secondaryTextColor = MaterialValueHelper.getSecondaryTextColor(this, ColorUtil.isColorLight(color));
        durationIconImageView.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN);
        songCountIconImageView.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN);
        albumCountIconImageView.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN);
        durationTextView.setTextColor(secondaryTextColor);
        songCountTextView.setTextColor(secondaryTextColor);
        albumCountTextView.setTextColor(secondaryTextColor);

        titleTextView.setTextColor(MaterialValueHelper.getPrimaryTextColor(this, ColorUtil.isColorLight(color)));
    }

    private void setUpToolbar() {
        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setTitle(null);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_artist_detail, menu);
        menu.findItem(R.id.action_colored_footers).setChecked(usePalette);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int id = item.getItemId();
        final ArrayList<Song> songs = songAdapter.getDataSet();
        if (id == R.id.action_sleep_timer) {
            new SleepTimerDialog().show(getSupportFragmentManager(), "SET_SLEEP_TIMER");
            return true;
        } else if (id == R.id.action_equalizer) {
            NavigationUtil.openEqualizer(this);
            return true;
        } else if (id == R.id.action_shuffle_artist) {
            MusicPlayerRemote.openAndShuffleQueue(songs, true);
            return true;
        } else if (id == R.id.action_play_next) {
            MusicPlayerRemote.playNext(songs);
            return true;
        } else if (id == R.id.action_add_to_current_playing) {
            MusicPlayerRemote.enqueue(songs);
            return true;
        } else if (id == R.id.action_add_to_playlist) {
            AddToPlaylistDialog.create(songs).show(getSupportFragmentManager(), "ADD_PLAYLIST");
            return true;
        } else if (id == android.R.id.home) {
            super.onBackPressed();
            return true;
        } else if (id == R.id.action_biography) {
            if (biographyDialog == null) {
                biographyDialog = new MaterialDialog.Builder(this)
                        .title(artist.getName())
                        .positiveText(android.R.string.ok)
                        .build();
            }
            if (PreferenceUtil.isAllowedToDownloadMetadata(this)) { // wiki should've been already downloaded
                if (biography != null) {
                    biographyDialog.setContent(biography);
                    biographyDialog.show();
                } else {
                    Toast.makeText(this, getResources().getString(R.string.biography_unavailable), Toast.LENGTH_SHORT).show();
                }
            } else { // force download
                biographyDialog.show();
                loadBiography();
            }
            return true;
        } else if (id == R.id.action_set_artist_image) {
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, getString(R.string.pick_from_local_storage)), REQUEST_CODE_SELECT_IMAGE);
            return true;
        } else if (id == R.id.action_reset_artist_image) {
            Toast.makeText(ArtistDetailActivity.this, getResources().getString(R.string.updating), Toast.LENGTH_SHORT).show();
            CustomArtistImageUtil.getInstance(ArtistDetailActivity.this).resetCustomArtistImage(artist);
            forceDownload = true;
            return true;
        } else if (id == R.id.action_colored_footers) {
            item.setChecked(!item.isChecked());
            setUsePalette(item.isChecked());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @NonNull
    @Override
    public AttachedCab openCab(int menuRes, @NonNull final CabCallbacks callbacks) {
        if (cab != null && AttachedCabKt.isActive(cab)) {AttachedCabKt.destroy(cab);}

        @ColorInt final int color = getPaletteColor();
        songAdapter.setColor(color);
        cab = MenuHelper.createAndOpenCab(this, menuRes, color, callbacks);
        return cab;
    }

    @Override
    public void onBackPressed() {
        if (cab != null && AttachedCabKt.isActive(cab)) {AttachedCabKt.destroy(cab);}
        else {
            albumRecyclerView.stopScroll();
            super.onBackPressed();
        }
    }

    @Override
    public void setStatusbarColor(int color) {
        super.setStatusbarColor(color);
        setLightStatusbar(false);
    }

    private void setArtist(final Artist artist) {
        this.artist = artist;
        loadArtistImage();

        if (PreferenceUtil.isAllowedToDownloadMetadata(this)) {
            loadBiography();
        }

        titleTextView.setText(artist.getName());
        songCountTextView.setText(MusicUtil.getSongCountString(this, artist.getSongCount()));
        albumCountTextView.setText(MusicUtil.getAlbumCountString(this, artist.getAlbumCount()));
        durationTextView.setText(MusicUtil.getReadableDurationString(MusicUtil.getTotalDuration(artist.getSongs())));

        songAdapter.swapDataSet(artist.getSongs());
        albumAdapter.swapDataSet(artist.albums);
    }

    private Artist getArtist() {
        if (artist == null) {artist = Artist.EMPTY;}
        return artist;
    }

    @Override
    @NonNull
    public Loader<Artist> onCreateLoader(int id, @NonNull final Bundle args) {
        return new AsyncArtistDataLoader(this, args.getLong(EXTRA_ARTIST_ID));
    }

    @Override
    public void onPlayingMetaChanged() {
        super.onPlayingMetaChanged();

        // give the adapter a chance to update the decoration
        songAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPlayStateChanged() {
        super.onPlayStateChanged();

        // give the adapter a chance to update the decoration
        songAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoadFinished(@NonNull final Loader<Artist> loader, final Artist data) {
        setArtist(data);
    }

    @Override
    public void onLoaderReset(@NonNull final Loader<Artist> loader) {
        this.artist = Artist.EMPTY;
        songAdapter.swapDataSet(artist.getSongs());
        albumAdapter.swapDataSet(artist.albums);
    }

    private static class AsyncArtistDataLoader extends WrappedAsyncTaskLoader<Artist> {
        private final long artistId;

        AsyncArtistDataLoader(final Context context, long artistId) {
            super(context);
            this.artistId = artistId;
        }

        @Override
        public Artist loadInBackground() {
            return ArtistLoader.getArtist(artistId);
        }
    }
}
