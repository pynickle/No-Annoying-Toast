package com.euphony.no_annoying_toast;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLLoader;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Iterator;

@OnlyIn(Dist.CLIENT)
public class BetterToastManager extends ToastManager {

    private Deque<BetterToastInstance<?>> topDownList = new ArrayDeque<>();

    public BetterToastManager() {
        super(Minecraft.getInstance(), new Options(Minecraft.getInstance(), FMLLoader.getGamePath().toFile()));
        this.queued = new ControlledDeque();
        this.occupiedSlots = new BitSet(ToastConfig.INSTANCE.toastCount.get());
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics) {
        if (!this.minecraft.options.hideGui) {
            int i = guiGraphics.guiWidth();

            for(ToastInstance<?> toastinstance : this.visibleToasts) {
                toastinstance.render(guiGraphics, i);
            }
        }
    }

    @Override
    public void update() {
        MutableBoolean mutableboolean = new MutableBoolean(false);
        this.visibleToasts.removeIf((p_392505_) -> {
            Toast.Visibility toast$visibility = p_392505_.visibility;
            p_392505_.update();
            if (p_392505_.visibility != toast$visibility && mutableboolean.isFalse()) {
                mutableboolean.setTrue();
                p_392505_.visibility.playSound(this.minecraft.getSoundManager());
            }

            if (p_392505_.hasFinishedRendering()) {
                this.occupiedSlots.clear(p_392505_.firstSlotIndex, p_392505_.firstSlotIndex + p_392505_.occupiedSlotCount);
                this.topDownList.remove(p_392505_);
                return true;
            } else {
                return false;
            }
        });
        if (!this.queued.isEmpty() && this.freeSlotCount() > 0) {
            this.queued.removeIf((p_392506_) -> {
                int i = p_392506_.occcupiedSlotCount();
                int j = this.findFreeSlotsIndex(i);
                if (j == -1) {
                    return false;
                } else {
                    this.visibleToasts.add(new ToastInstance<>(p_392506_, j, i));
                    this.occupiedSlots.set(j, j + i);
                    this.topDownList.forEach(t -> t.animationStartTime = -1L);
                    this.topDownList.addFirst(new BetterToastInstance<>(p_392506_, j, i));
                    SoundEvent soundevent = p_392506_.getSoundEvent();
                    if (soundevent != null && this.playedToastSounds.add(soundevent)) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(soundevent, 1.0F, 1.0F));
                    }

                    return true;
                }
            });
        }

        this.playedToastSounds.clear();
    }

    @Override
    public void clear() {
        super.clear();
        this.topDownList.clear();
    }

    @Override
    public int findFreeSlotsIndex(int pSlotCount) {
        if (this.freeSlotCount() >= pSlotCount) {
            int i = 0;

            for (int j = 0; j < ToastConfig.INSTANCE.toastCount.get(); ++j) {
                if (this.occupiedSlots.get(j)) {
                    i = 0;
                }
                else {
                    ++i;
                    if (i == pSlotCount) {
                        return j + 1 - i;
                    }
                }
            }
        }

        return -1;
    }

    @Override
    public int freeSlotCount() {
        return ToastConfig.INSTANCE.toastCount.get() - this.occupiedSlots.cardinality();
    }

    @OnlyIn(Dist.CLIENT)
    public class BetterToastInstance<T extends Toast> extends ToastInstance<T> {

        protected int forcedShowTime = 0;

        protected BetterToastInstance(T toast, int index, int slotCount) {
            super(toast, index, slotCount);
            NoAnnoyingToast.tracker.add(this);
        }

        public boolean tick() {
            return this.forcedShowTime++ > ToastConfig.INSTANCE.forceTime.get();
        }

        @Override
        public void calculateVisiblePortion(long visibilityTime) {
            float f = Mth.clamp((float)(visibilityTime - this.animationStartTime) / 600.0F, 0.0F, 1.0F);
            f *= f;
            if (ToastConfig.INSTANCE.noSlide.get()) return;
            if (this.forcedShowTime > ToastConfig.INSTANCE.forceTime.get() && this.visibility == Toast.Visibility.HIDE) {
                this.visiblePortion = 1.0F - f;
            } else {
                this.visiblePortion = f;
            }

        }

        @Override
        public void render(GuiGraphics guiGraphics, int guiWidth) {
            long sysTime = Util.getMillis();
            this.calculateVisiblePortion(sysTime);
            int trueIdx = 0;
            Matrix3x2fStack stack = guiGraphics.pose();
            stack.pushMatrix();

            if (ToastConfig.INSTANCE.topDown.get()) {
                int x = ToastConfig.INSTANCE.startLeft.get() ? 0 : guiWidth - this.toast.width();
                stack.translate(x, (trueIdx - 1) * this.toast.height() + this.toast.height() * this.visiblePortion);
            }
            else if (ToastConfig.INSTANCE.startLeft.get()) {
                stack.translate(-this.toast.width() + this.toast.width() * this.visiblePortion, this.firstSlotIndex * this.toast.height());
            }
            else {
                stack.translate(guiWidth - this.toast.width() * this.visiblePortion, this.firstSlotIndex * this.toast.height());
            }

            stack.translate(ToastConfig.INSTANCE.offsetX.get(), ToastConfig.INSTANCE.offsetY.get());
            Toast.Visibility visibility = Toast.Visibility.SHOW;
            if (this.animationStartTime != -1) this.toast.render(guiGraphics, BetterToastManager.this.minecraft.font, sysTime - this.becameFullyVisibleAt);
            stack.popMatrix();
        }

        @Override
        public void update() {
            long i = Util.getMillis();
            int trueIdx = 0;

            if (ToastConfig.INSTANCE.topDown.get()) {
                Iterator<BetterToastInstance<?>> it = BetterToastManager.this.topDownList.iterator();
                while (it.hasNext()) {
                    var next = it.next();
                    if (next == this) {
                        break;
                    }
                    trueIdx++;
                }
            }
            if (this.animationStartTime == -1L) {
                this.animationStartTime = i;
                this.visibility = Toast.Visibility.SHOW;
            }

            if (this.visibility == Toast.Visibility.SHOW && i - this.animationStartTime <= 600L) {
                this.becameFullyVisibleAt = i;
            }

            this.fullyVisibleFor = i - this.becameFullyVisibleAt;
            this.calculateVisiblePortion(i);
            this.toast.update(BetterToastManager.this, this.fullyVisibleFor);
            Toast.Visibility toast$visibility = this.toast.getWantedVisibility();
            if (toast$visibility != this.visibility) {
                this.animationStartTime = i - (long)((int)((1.0F - this.visiblePortion) * 600.0F));
                this.visibility = toast$visibility;
            }

            this.hasFinishedRendering = this.visibility == Toast.Visibility.HIDE && i - this.animationStartTime > 600L;
        }
    }
}
