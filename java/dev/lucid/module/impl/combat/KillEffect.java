package dev.lucid.module.impl.combat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import dev.lucid.module.Category;
import dev.lucid.module.Module;
import dev.lucid.setting.BooleanSetting;
import dev.lucid.setting.ModeSetting;
import dev.lucid.setting.SliderSetting;
import dev.lucid.util.math.MathUtil;
import dev.lucid.util.render.ColorUtil;
import dev.lucid.util.render.Render3D;
/**
 * Добивание: на месте убитого появляются мечи, замахиваются и рубят его напоследок.
 *
 * <p>Модуль состоит из двух независимых половин, и это главное, что про него нужно знать.
 * Первая — определение убийства, она живёт в тике. Вторая — сама анимация, она живёт в
 * кадре. Между ними ровно одна связь: список {@link Effect}. Так сделано потому, что тик
 * идёт двадцать раз в секунду, а кадров бывает и триста: считай анимацию в тике, мечи
 * дёргались бы ступеньками.</p>
 *
 * <p>Время эффекта берётся не в тиках, а в наносекундах от старта. Тику здесь нечего дать:
 * убитая сущность через секунду уже исчезнет с сервера, интерполировать не от чего, и
 * эффект всё равно живёт сам по себе — от точки смерти, а не от сущности.</p>
 *
 * <p>Определить своё убийство на клиенте нельзя точно: сервер не присылает, кто кого убил.
 * Поэтому здесь честная эвристика — «сущность потеряла здоровье в тот момент, когда я махал
 * рукой в её сторону и она была в досягаемости». Ошибиться она может только в кучном бою,
 * где рядом бьют по той же цели, и цена ошибки — лишний или пропущенный эффект.</p>
 */
public class KillEffect extends Module {
	/** Колющий: ширина веера и заодно длина облёта ведущего меча, в градусах. */
	private static final float STAB_ARC = 110.0F;
	/**
	 * Колющий: сколько секунд занимает выход ведущего меча — проявление на месте
	 * плюс сама дуга от правого края веера к левому.
	 *
	 * <p>Фазы колющего стоят в секундах, а не в долях длительности, и это принципиально.
	 * Тело падает свои двадцать тиков независимо от настроек модуля, а удар должен прийти
	 * в уже упавшее тело. На долях это ломалось от первого же движения ползунка:
	 * на трёх секундах мечи били бы по трупу, а на семи — в пустое место, где труп был
	 * секунды четыре назад. В секундах же ползунок длительности решает только одно —
	 * сколько мечи потом торчат в теле и как долго тают.</p>
	 */
	private static final float STAB_TRAVEL_SEC = 0.62F;
	/** Колющий: сколько секунд веер довзводится, повиснув остриями вниз. */
	private static final float STAB_RAISE_SEC = 0.20F;
	/** Колющий: сколько секунд идёт само падение клинков в тело. */
	private static final float STAB_DROP_SEC = 0.22F;
	/** Колющий: какую долю остатка мечи торчат в полную силу, прежде чем таять. */
	private static final float STAB_HOLD = 0.45F;
	/**
	 * Колющий: с какой доли остатка тело начинает таять.
	 *
	 * <p>Мечи гаснут заметно раньше, но тело обязано оставаться плотным
	 * почти до самого конца: полупрозрачный труп посреди анимации читается
	 * как баг, а не как эффект, так что у тела своя полка, позже клинковых.</p>
	 */
	private static final float CORPSE_MELT = 0.82F;
	/** Где кончается остриё в модели меча: от рукояти вверх, в долях размера. */
	private static final float SWORD_TIP = 0.85F;
	/**
	 * Колющий: на какой доле толщины лежащего тела клинок входит внутрь.
	 *
	 * <p>Это граница двух проходов, а не срез: выше неё клинок рисуется как
	 * раньше — сквозь стены, ниже — с проверкой глубины, и там его закрывает
	 * само тело. Поэтому ошибка здесь перевернулась. Промах вниз плох: полоса
	 * клинка внутри туши уйдёт в проход «сквозь стены» и будет видна насквозь.
	 * Промах вверх почти безобиден: над телом клинок нарисуется обычным
	 * образом, закрывать его там нечему. Держим плоскость у расчётного верха
	 * лежачей туши.</p>
	 */
	private static final double STAB_ENTRY = 1.05;
	/**
	 * Колющий: на сколько толщины тела остриё уходит ниже точки прицела.
	 *
	 * <p>Это и есть глубина посадки. Малое значение читается не как удар, а
	 * как меч, приставленный к телу сверху: рукоять остаётся высоко, в тушу
	 * уходит пара сантиметров клинка, и всё вместе выглядит так, будто меч
	 * сейчас повалится. Ошибка вглубь тут безобидна: остриё, ушедшее ниже тела,
	 * скрывает земля — тем же проходом с проверкой глубины.</p>
	 */
	private static final double STAB_SINK = 0.80;
	/**
	 * Колющий: гарантированный укус в землю, в долях длины клинка.
	 *
	 * <p>Глубина от толщины тела сама по себе не гарантирует ничего: у
	 * тонкой туши остриё приходит выше уровня земли, и клинок честно
	 * висит в воздухе. Поэтому берём то, что глубже: расчёт от тела или
	 * этот минимум. Он же решает дело в большинстве случаев: расчёт
	 * от тела выигрывает только у крупных целей.</p>
	 *
	 * <p>Меряется именно в долях клинка, а не в блоках: размер меча
	 * зависит от габарита цели, и абсолютная глубина была бы для
	 * курицы половиной клинка, а для голема — царапиной. От длины
	 * клинка доля видимого металла держится одинаковой на любой цели.</p>
	 */
	private static final double STAB_GROUND = 0.20;
	/**
	 * Колющий: какую долю габарита занимает лежачее тело в высоту.
	 *
	 * <p>Габарит живой цели здесь врёт в полтора-два раза: свинья ростом 0.9
	 * в лежачем виде — лепёшка толщиной около 0.6, а игрок при росте 1.8 и
	 * вовсе тоньше. Значение задаёт и глубину посадки, и высоту плоскости
	 * входа, но срезом больше не управляет. Занизим — клинок сядет
	 * поверхностно, завысим — остриё уйдёт в землю, где его и не видно.
	 * Поэтому целимся заведомо низко, у самой земли.</p>
	 */
	private static final double STAB_LYING = 0.62;
	/** Колющий: потолок толщины лежачего тела в блоках — ради крупных целей. */
	private static final double STAB_LYING_MAX = 0.6;
	/**
	 * Разлёт колющих клинков: доля габарита туши и нижний порог в блоках.
	 *
	 * <p>Порог нужен ради мелких целей: в курице кольцо от одной ширины тела
	 * схлопывалось в точку, и три меча вставали одной кучей.</p>
	 */
	private static final double STAB_SPREAD = 1.15;
	private static final double STAB_MIN_SPREAD = 0.75;
	/** Шаг между воткнутыми клинками вдоль тела — в долях его длины. */
	private static final double BODY_STEP = 0.30;
	/**
	 * Размер клинков считается от цели: человек — это единица.
	 *
	 * <p>Без этого в лисе или курице торчат три меча ростом больше самой
	 * туши, и вся сцена читается как частокол вокруг трупа, а не как добивание.</p>
	 */
	private static final double BODY_REF = 0.95;
	private static final double BODY_SCALE_MIN = 0.5;
	private static final double BODY_SCALE_MAX = 1.35;
	/** Сколько тиков идёт ванильная заваливка тела на бок. */
	private static final int FALL_LEAN_TICKS = 20;
	/** Предел ожидания падения: дальше начинаем, где бы тело ни было. */
	private static final int WAIT_MAX_TICKS = 60;
	/** На какую долю роста центр лежащего трупа уезжает вбок от его координат. */
	private static final float CORPSE_SHIFT = 0.4F;
	/**
	 * Разброс расстановки: градусы по углу, доля радиуса и блоки по высоте.
	 *
	 * <p>Ровный веер читается как чертёж, а не как удар: три клинка стоят
	 * через один и тот же угол и на одной высоте каждое добивание. Запас взят
	 * небольшой: больше — и веер рассыпается в беспорядок, а мечи лезут мимо туши.</p>
	 */
	private static final float JITTER_YAW = 18.0F;
	private static final float JITTER_RADIUS = 0.22F;
	private static final float JITTER_LIFT = 0.07F;
	/**
	 * Колющий: разброс уровня посадки в долях толщины лежачего тела.
	 *
	 * <p>Тело — не плоскость, и три клинка, вынырнувшие из туши ровно на
	 * одной высоте, читаются как чертёж, а не как удар.</p>
	 */
	private static final float JITTER_SEAT = 0.25F;
	/** Свой сдвиг подлёта у каждого клинка, в секундах: залп не должен щёлкать разом. */
	private static final float JITTER_TIME = 0.07F;
	/** Насколько может отличаться темп всего залпа: 0.06 — это плюс-минус 6% скорости. */
	private static final float PACE_SPREAD = 0.06F;
	/**
	 * Предел скорости разворота веера за игроком, градусов в секунду.
	 *
	 * <p>Угол на цель вблизи растёт лавинообразно: один шаг в полуметре от
	 * трупа разворачивает веер на десятки градусов, тогда как в десяти
	 * блоках тот же шаг даёт меньше градуса. Оттуда и ощущение, будто
	 * клинки трясёт, стоит к ним подойти. Предел снимает именно этот
	 * всплеск, оставляя обычное слежение на нормальной дистанции.</p>
	 */
	private static final float VIEW_YAW_RATE = 210.0F;
	/** Потолок шага кадра в секундах: после лага веер не должен прыгать. */
	private static final float VIEW_FRAME_MAX = 0.1F;
	/** Отдача после удара: во сколько раз в секунду затухает качание клинка. */
	private static final float RECOIL_DAMP = 7.0F;
	/** Отдача: угловая скорость качания, радиан в секунду. */
	private static final float RECOIL_RATE = 26.0F;
	/** Отдача: размах по наклону клинка, в градусах. */
	private static final float RECOIL_SWING = 3.2F;
	/** Отдача: размах по высоте рукояти, в долях длины клинка. */
	private static final float RECOIL_LIFT = 0.08F;
	/**
	 * Разнобой самих клинков: наклон, крен и длина.
	 *
	 * <p>Сдвигать мечи по кругу оказалось мало: три строго вертикальных клинка
	 * одной длины читаются как чертёж при любой расстановке. Глаз цепляется
	 * за параллельность, а не за шаг между рукоятями.</p>
	 */
	private static final float JITTER_SWING = 9.0F;
	private static final float JITTER_ROLL = 12.0F;
	/**
	 * Боковое заваливание клинка — единственный разброс, который реально
	 * меняет угол стояния.
	 *
	 * <p>Крен выше вращает меч вокруг собственной оси и на угол не влияет
	 * вовсе, а наклон идёт в плоскости замаха, одной и той же у всего
	 * залпа. Поэтому три клинка валились на разную величину, но в одну
	 * сторону, и глаз читал это как один общий угол. Заваливание идёт
	 * поперёк той плоскости, так что у каждого меча появляется своё
	 * направление наклона, а не только своя величина.</p>
	 */
	private static final float JITTER_LEAN = 6.0F;
	private static final float JITTER_SIZE = 0.14F;
	/** Разворот всего веера и его ширина — свои на каждое добивание. */
	private static final float FAN_TWIST = 26.0F;
	private static final float FAN_SPREAD = 0.3F;
	/** Колющий: за сколько секунд силуэт наливается в полноценный меч. */
	private static final float STAB_FADE_SEC = 0.18F;
	/**
	 * Колющий: сколько секунд ведущий клинок проявляется на месте, прежде чем уйти.
	 *
	 * <p>Появляться на ходу нельзя: дуга идёт с выбегом, и самый быстрый её участок —
	 * первый. Клинок, который проявляется и одновременно смазывается влево, читается как
	 * рывок, а не как появление. Пауза сидит внутри {@link #STAB_TRAVEL_SEC}, так что
	 * момент удара от неё не едет и вся остальная раскладка фаз остаётся как была.</p>
	 */
	private static final float STAB_APPEAR_SEC = 0.12F;
	/** Во сколько клинок мельче в начале проявления: подрастание вместо выскакивания. */
	private static final float STAB_GROW = 0.72F;
	private static final String COLOR_CYAN = "Бирюзовый";
	private static final String COLOR_RED = "Красный";
	private static final String COLOR_PURPLE = "Фиолетовый";
	private static final String COLOR_GOLD = "Золотой";
	private static final String COLOR_WHITE = "Белый";
	/** Больше эффектов разом не держим: на массовом добивании это только каша на экране. */
	private static final int MAX_EFFECTS = 6;
	/** Сколько тиков помним свой удар по цели. Секунда с лишним — на добивание с отходом. */
	private static final int HIT_MEMORY_TICKS = 40;
	/** Насколько свежим должен быть взмах руки, чтобы считать урон своим. */
	private static final int SWING_MEMORY_TICKS = 4;
	/** Ванильная досягаемость с запасом на лаг. */
	private static final double REACH = 6.5;
	/** Цель должна быть примерно перед носом: косинус угла между взглядом и целью. */
	private static final double AIM_DOT = 0.35;
	/** Сущность пропала из мира — сколько тиков ждать, прежде чем забыть о ней. */
	private static final int FORGET_TICKS = 40;
	/**
	 * Роли части: кожа рукояти, латунь, сталь, темнота дола, светящаяся кромка и
	 * приглушённый обух.
	 *
	 * <p>Обух вынесен в отдельную роль именно потому, что почти чёрным он читался
	 * второй тёмной полосой рядом с долом: две полосы под углом друг к другу, и
	 * центровка дола переставала быть видной. Темнота на клинке должна быть одна.</p>
	 */
	private static final int ROLE_GRIP = 0;
	private static final int ROLE_METAL = 1;
	private static final int ROLE_STEEL = 2;
	private static final int ROLE_DARK = 3;
	private static final int ROLE_EDGE = 4;
	private static final int ROLE_SPINE = 5;
	/**
	 * Тона частей.
	 *
	 * <p>Главное здесь — разница яркостей, а не сами цвета. Сталь берётся средней, а не
	 * почти белой: если клинок светлый, то светлой кромке негде выделиться, и всё сливается
	 * в одно светящееся пятно — ровно то, отчего меч выглядел пластиковой трубкой.</p>
	 */
	private static final int TONE_GRIP = 0x4A2F1B;
	private static final int TONE_METAL = 0xD9B356;
	private static final int TONE_STEEL = 0x8FA8C8;
	private static final int TONE_DARK = 0x1B2430;
	private static final int TONE_EDGE = 0xFFFFFF;
	private static final int TONE_SPINE = 0x60738D;
	/**
	 * Части меча в его собственной системе: смещение от хвата и размеры коробки.
	 *
	 * <p>Ноль — место хвата, клинок смотрит в плюс по Y. Пивот именно в хвате, а не в центре
	 * модели: меч должен рубить от рукояти, как в руке, иначе замах выглядит как вращение
	 * палки вокруг середины.</p>
	 *
	 * <p>Толщина клинка идёт по X, а ширина лезвие-обух — по Z. Замах есть поворот
	 * вокруг X, то есть острие летит по Z: значит ширина лежит в плоскости удара и меч
	 * входит в цель кромкой, а не плоскостью. По той же причине гарда тянется по Z, а не
	 * по X: у настоящего меча её рога лежат в плоскости замаха, и именно они глаз читает
	 * как «каким боком повёрнут клинок». Гарда по X давала обратную подсказку, и удар
	 * казался плоским даже тогда, когда геометрически был верным.</p>
	 *
	 * <p>Клинок собран из нескольких коробок, а не из одной: одна коробка на всю длину
	 * читается как брусок. Здесь есть пятка, два сегмента клинка, два сужения и острие,
	 * а по Z вынесены кромка спереди и тёмный обух сзади. Они же служат подсказкой,
	 * какой стороной меч идёт вперёд.</p>
	 *
	 * <p>Главное правило списка: части только стыкуются и никогда не пересекаются. Меч
	 * полупрозрачный, и в месте наложения цвет складывается дважды, а почти совпавшие
	 * грани ещё и спорят за глубину. Именно поэтому тёмный дол, сидевший внутри клинка
	 * с выступом в две тысячных, менял форму и место на каждом повороте. Теперь
	 * дол — не отдельная коробка на плоскости, а тёмная полоса в самой толще металла:
	 * клинок разрезан по ширине на три полосы одной толщины, и средняя из них тёмная.</p>
	 *
	 * <p>Приподнятая над плоскостью деталь всегда «уезжает» по клинку, и это не ошибка
	 * расчёта, а параллакс: у накладки и у самой плоскости разная глубина, поэтому при
	 * повороте они смещаются относительно друг друга на экране. Пять тысячных блока
	 * над поверхностью — уже заметный сдвиг. Полоса внутри металла не уезжает никуда,
	 * потому что она и есть металл, а не наклейка поверх него.</p>
	 */
	private static final Part[] SWORD_PARTS = {
			// смещение X, Y, Z, толщина X, длина Y, ширина Z, роль
			new Part(0.0F, -0.243F, 0.0F, 0.062F, 0.056F, 0.068F, ROLE_METAL), // яблоко
			new Part(0.0F, -0.185F, 0.0F, 0.044F, 0.060F, 0.044F, ROLE_GRIP), // рукоять низ
			new Part(0.0F, -0.140F, 0.0F, 0.048F, 0.030F, 0.048F, ROLE_DARK), // кольцо низ
			new Part(0.0F, -0.095F, 0.0F, 0.044F, 0.060F, 0.044F, ROLE_GRIP), // рукоять центр
			new Part(0.0F, -0.050F, 0.0F, 0.048F, 0.030F, 0.048F, ROLE_DARK), // кольцо верх
			new Part(0.0F, -0.025F, 0.0F, 0.044F, 0.020F, 0.044F, ROLE_GRIP), // рукоять верх
			new Part(0.0F, 0.009F, 0.0F, 0.044F, 0.048F, 0.310F, ROLE_METAL), // гарда
			new Part(0.0F, 0.078F, 0.0F, 0.032F, 0.090F, 0.104F, ROLE_STEEL), // пятка
			new Part(0.0F, 0.293F, 0.0305F, 0.028F, 0.340F, 0.027F, ROLE_STEEL), // клинок низ, полоса у кромки
			new Part(0.0F, 0.293F, 0.0F, 0.028F, 0.340F, 0.034F, ROLE_DARK), // клинок низ, дол
			new Part(0.0F, 0.293F, -0.0305F, 0.028F, 0.340F, 0.027F, ROLE_STEEL), // клинок низ, полоса у обуха
			new Part(0.0F, 0.5315F, 0.0255F, 0.026F, 0.137F, 0.025F, ROLE_STEEL), // клинок верх, полоса у кромки
			new Part(0.0F, 0.5315F, 0.0F, 0.026F, 0.137F, 0.026F, ROLE_DARK), // клинок верх, дол
			new Part(0.0F, 0.5315F, -0.0255F, 0.026F, 0.137F, 0.025F, ROLE_STEEL), // клинок верх, полоса у обуха
			new Part(0.0F, 0.6365F, 0.022F, 0.026F, 0.073F, 0.032F, ROLE_STEEL), // сход дола, полоса у кромки
			new Part(0.0F, 0.6365F, 0.0F, 0.026F, 0.073F, 0.012F, ROLE_DARK), // сход дола
			new Part(0.0F, 0.6365F, -0.022F, 0.026F, 0.073F, 0.032F, ROLE_STEEL), // сход дола, полоса у обуха
			new Part(0.0F, 0.713F, 0.0F, 0.024F, 0.080F, 0.060F, ROLE_STEEL), // сужение
			new Part(0.0F, 0.783F, 0.0F, 0.020F, 0.060F, 0.040F, ROLE_STEEL), // сужение у острия
			new Part(0.0F, 0.833F, 0.0F, 0.016F, 0.040F, 0.024F, ROLE_EDGE), // острие
			new Part(0.0F, 0.293F, 0.052F, 0.020F, 0.340F, 0.016F, ROLE_EDGE), // кромка низ
			new Part(0.0F, 0.568F, 0.046F, 0.018F, 0.210F, 0.016F, ROLE_EDGE), // кромка верх
			new Part(0.0F, 0.293F, -0.052F, 0.022F, 0.340F, 0.016F, ROLE_SPINE), // обух низ
			new Part(0.0F, 0.568F, -0.046F, 0.020F, 0.210F, 0.016F, ROLE_SPINE), // обух верх
	};
	/** Сколько призрачных копий тянется за клинком на ударе. */
	private static final int TRAIL_STEPS = 3;
	// ------------------------------------------------------------------ настройки
	private final SliderSetting swords = this.addSetting(new SliderSetting(
			"swords", "Мечей", "Сколько мечей появляется",
			3.0, 1.0, 6.0, 1.0));
	/**
	 * Вся скорость анимации живёт здесь: фазы считаются в долях от этого числа.
	 *
	 * <p>id с суффиксом, а не просто {@code duration}: старые конфиги хранят быструю
	 * секунду, а конфиг всегда сильнее дефолта — под прежним ключом новая скорость просто
	 * не применилась бы на уже запущенном клиенте.</p>
	 */
	private final SliderSetting duration = this.addSetting(new SliderSetting(
			"duration_sec", "Длительность", "Секунды от появления до исчезновения",
			3.0, 1.2, 7.5, 0.1));
	private final SliderSetting scale = this.addSetting(new SliderSetting(
			"scale", "Размер", "Размер мечей",
			1.0, 0.4, 2.0, 0.05));
	private final SliderSetting radius = this.addSetting(new SliderSetting(
			"radius", "Радиус", "На каком расстоянии от цели встают мечи",
			0.9, 0.3, 2.5, 0.05));
	private final SliderSetting height = this.addSetting(new SliderSetting(
			"height", "Высота", "Доля роста цели, на которой висят мечи",
			0.7, 0.0, 1.5, 0.05));
	private final ModeSetting color = this.addSetting(new ModeSetting(
			"color", "Цвет", "Цвет мечей",
			COLOR_CYAN, List.of(COLOR_CYAN, COLOR_RED, COLOR_PURPLE, COLOR_GOLD, COLOR_WHITE)));
	// Идентификатор новый, потому что сохранённая настройка перебивает значение по
	// умолчанию. На 220 клинок просвечивал, и сквозь него было видно ребро с обратной
	// стороны — тёмное пятно казалось плавающим. Нижняя граница поднята: ниже 140
	// меч снова становится стеклянным, и вся внутренняя геометрия лезет наружу.
	private final SliderSetting opacity = this.addSetting(new SliderSetting(
			"density", "Плотность", "Насколько мечи непрозрачные",
			255.0, 140.0, 255.0, 5.0));
	private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting(
			"through_walls", "Сквозь стены", "Видно даже за блоками", true));
	private final BooleanSetting glow = this.addSetting(new BooleanSetting(
			"glow", "Свечение", "Полупрозрачный ореол вокруг клинка", true));
	private final BooleanSetting trail = this.addSetting(new BooleanSetting(
			"trail", "След", "Шлейф за клинком на ударе", true));
	private final BooleanSetting particles = this.addSetting(new BooleanSetting(
			"particles", "Частицы", "Искры в момент удара", true));
	private final BooleanSetting sound = this.addSetting(new BooleanSetting(
			"sound", "Звук", "Свист меча при появлении", true));
	private final BooleanSetting onlyMine = this.addSetting(new BooleanSetting(
			"only_mine", "Только мои убийства", "Не реагировать на чужие смерти", true));
	/**
	 * Держать тело убитого до конца анимации.
	 *
	 * <p>Ваниль убирает труп через двадцать тиков после смерти, то есть примерно
	 * через секунду, и мечи оставались торчать в пустоте. С этой галкой тело
	 * лежит всю анимацию и в конце тает вместе с клинками.</p>
	 *
	 * <p>Задержка чисто клиентская и никому, кроме глаз, не видна: на сервере трупа
	 * уже нет, лут выпал, опыт начислен. На игроках большинства серверов толку от
	 * неё мало: их убирают без анимации смерти вместе с последним ударом.</p>
	 */
	private final BooleanSetting holdBody = this.addSetting(new BooleanSetting(
			"hold_body", "Держать тело", "Труп лежит всю анимацию и тает вместе с мечами", true));
	/**
	 * Ждать, пока тело упадёт.
	 *
	 * <p>Без неё эффект начинается в момент смерти, и мечи едут вместе с трупом,
	 * которого ещё несёт от удара, — сразу видно, что они ни во что не втыкаются.
	 * С ней клинки появляются, когда тело долетело до земли и улеглось на бок.</p>
	 *
	 * <p>Ожидание ограничено тремя секундами: тело в воде или на льду может не
	 * успокоиться никогда, и эффект в таком случае начинается сам.</p>
	 */
	private final BooleanSetting waitFall = this.addSetting(new BooleanSetting(
			"wait_fall", "Ждать падения", "Мечи появляются, когда тело упало и улеглось", true));
	/**
	 * Не давать телу двигаться, пока идёт анимация.
	 *
	 * <p>Мечи воткнуты в конкретную точку, и любое движение трупа под ними сразу
	 * видно. А толкает его всё подряд: остаточная инерция от удара, течение, лёд,
	 * другие мобы и сам игрок.</p>
	 *
	 * <p>Работает с того мгновения, как тело улеглось, то есть только вместе с
	 * ожиданием падения: иначе труп застыл бы в воздухе на месте смерти.</p>
	 */
	private final BooleanSetting freezeBody = this.addSetting(new BooleanSetting(
			"freeze_body", "Закрепить тело", "Труп стоит на месте, пока в нём мечи", true));
	private final SliderSetting range = this.addSetting(new SliderSetting(
			"range", "Дистанция", "На каком расстоянии отслеживать смерти",
			24.0, 4.0, 64.0, 1.0));
	// ------------------------------------------------------------------ состояние
	/**
	 * Живые эффекты. Список на {@code CopyOnWriteArrayList} не ради потоков, а ради того,
	 * что тик добавляет и удаляет эффекты, а кадр в это же время по ним идёт.
	 */
	private final List<Effect> effects = new CopyOnWriteArrayList<>();
	/** id сущности -> то, что мы о ней помним с прошлого тика. */
	private final Map<Integer, Track> tracks = new HashMap<>();
	/** Свой счётчик тиков: игровое время сервером может дёргаться, а этот только растёт. */
	private int ticks;
	private int lastSwingTick = Integer.MIN_VALUE;
	/** Мир, в котором собрано состояние. Сменился — всё, что помним, уже про другой мир. */
	private ClientLevel level;
	/** Кватернион и вектор переиспользуются: они нужны на каждую часть каждого меча в кадре. */
	private final Quaternionf rotation = new Quaternionf();
	private final Vector3f offset = new Vector3f();
	/** Поза меча в текущем кадре. Одна на всех: считается и сразу же рисуется. */
	private final Pose pose = new Pose();
	/**
	 * Экземпляр для миксинов: своей ссылки у них нет, они зовут модуль статически.
	 *
	 * <p>Модуль создаётся один раз на старте клиента, так что перезаписи здесь не бывает.</p>
	 */
	private static KillEffect instance;
	/**
	 * Цвет тела на время одного {@code submit}.
	 *
	 * <p>Цвет считается там, где видна сущность, а подставляется там, где её уже нет:
	 * в {@code ModifyArg} есть только int. Отрисовка однопоточная, так что одного поля
	 * хватает.</p>
	 */
	private static int submitTint;
	/** Мы сами просим убрать тело — миксин не должен отменять это удаление. */
	private boolean releasing;
	public KillEffect() {
		super("kill_effect", "KillEffect", Category.COMBAT,
				"Мечи добивают убитого");
		instance = this;
	}
	/**
	 * Регистрация в мировом рендере — один раз на весь клиент.
	 *
	 * <p>От рендер-событий отписаться нельзя, поэтому включённость проверяется внутри
	 * {@link #extract(Render3D.Frame)}, а не здесь.</p>
	 */
	@Override
	public void onRegister() {
		Render3D.register(this::extract);
	}
	@Override
	protected void onDisable() {
		// Модуль выключили посередине эффекта: задержанные тела надо отпустить,
		// иначе они останутся лежать на клиенте до перезахода.
		for (Effect effect : this.effects) {
			this.releaseBody(effect);
		}
		this.effects.clear();
		this.tracks.clear();
	}
	// ------------------------------------------------------------------ поиск убийств
	@Override
	public void onTick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel currentLevel = client.level;
		if (player == null || currentLevel == null) {
			return;
		}
		// Мир сменился: старые id принадлежат другим сущностям, а старые координаты —
		// другому месту. Держать это дальше опаснее, чем потерять один эффект.
		if (this.level != currentLevel) {
			this.level = currentLevel;
			this.tracks.clear();
			this.effects.clear();
		}
		this.ticks++;
		if (player.swinging) {
			this.lastSwingTick = this.ticks;
		}
		this.scanEntities(client, player, currentLevel);
		this.sweepTracks();
		this.tickEffects(currentLevel);
	}
	/** Обходит живых вокруг: обновляет память о них и ловит момент смерти. */
	private void scanEntities(Minecraft client, LocalPlayer player, ClientLevel currentLevel) {
		double limit = this.range.get();
		double limitSqr = limit * limit;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		for (Entity entity : currentLevel.entitiesForRendering()) {
			if (entity == player || !(entity instanceof LivingEntity living)) {
				continue;
			}
			if (living.distanceToSqr(player) > limitSqr) {
				continue;
			}
			Track track = this.tracks.get(entity.getId());
			if (track == null) {
				track = new Track(living, this.ticks);
				this.tracks.put(entity.getId(), track);
				// Сущность, которую мы впервые увидели уже мёртвой, добивать нечем:
				// это чужой труп, докатившийся до нас в анимации смерти.
				track.triggered = living.isDeadOrDying();
				continue;
			}
			// hurtTime сервер ставит на максимум при каждом уроне и сам его уменьшает,
			// поэтому его рост — признак нового удара, даже если здоровье не пришло.
			boolean damaged = living.hurtTime > track.hurtTime || living.getHealth() < track.health;
			if (damaged && this.isMyHit(living, eye, look)) {
				track.attackedTick = this.ticks;
			}
			track.update(living, this.ticks);
			if (!track.triggered && (living.isDeadOrDying() || living.getHealth() <= 0.0F)) {
				track.triggered = true;
				this.trigger(client, track);
			}
		}
	}
	/**
	 * Урон нанесли мы?
	 *
	 * <p>Три условия сразу: рука махала только что, цель в досягаемости и цель перед нами.
	 * По отдельности каждое ловит лишнее — сосед бьёт ту же цель, мы рубим блок рядом с
	 * дракой, цель за спиной падает с обрыва.</p>
	 */
	private boolean isMyHit(LivingEntity target, Vec3 eye, Vec3 look) {
		if (this.ticks - this.lastSwingTick > SWING_MEMORY_TICKS) {
			return false;
		}
		Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
		double distance = toTarget.length();
		if (distance > REACH) {
			return false;
		}
		// Вплотную направление взгляда уже ни о чём не говорит — центр цели может
		// оказаться и позади глаз.
		if (distance < 0.5) {
			return true;
		}
		return toTarget.normalize().dot(look) > AIM_DOT;
	}
	/**
	 * Сущности, которые пропали из мира.
	 *
	 * <p>Отдельная ветка нужна из-за игроков: на большинстве серверов убитого игрока просто
	 * убирают с клиента, без анимации смерти. Ловим это по последнему, что о нём знали:
	 * мы били его только что, и здоровья у него оставалось мало.</p>
	 */
	private void sweepTracks() {
		Iterator<Map.Entry<Integer, Track>> iterator = this.tracks.entrySet().iterator();
		while (iterator.hasNext()) {
			Track track = iterator.next().getValue();
			if (track.seenTick == this.ticks) {
				continue;
			}
			boolean freshHit = this.ticks - track.attackedTick <= HIT_MEMORY_TICKS;
			boolean nearlyDead = track.health <= track.maxHealth * 0.5F;
			if (!track.triggered && freshHit && nearlyDead) {
				track.triggered = true;
				this.trigger(Minecraft.getInstance(), track);
			}
			if (this.ticks - track.seenTick > FORGET_TICKS) {
				iterator.remove();
			}
		}
	}
	/** Запускает эффект, если убийство наше (или нам всё равно, чьё оно). */
	private void trigger(Minecraft client, Track track) {
		if (this.onlyMine.get() && this.ticks - track.attackedTick > HIT_MEMORY_TICKS) {
			return;
		}
		int count = this.swords.getAsInt();
		long length = (long) (this.duration.get() * 1_000_000_000.0);
		// Разворот случайный: иначе на всех убийствах мечи стоят одинаково и эффект
		// начинает выглядеть как декаль, а не как событие.
		float baseYaw = MathUtil.random(0.0F, 360.0F);
		if (this.effects.size() >= MAX_EFFECTS) {
			this.releaseBody(this.effects.get(0));
			this.effects.remove(0);
		}
		Effect effect = new Effect(
				track.body, track.x, track.y, track.z, track.height, track.width,
				System.nanoTime(), length, baseYaw, count);
		// Ждать имеет смысл только за телом: без него падать нечему.
		effect.waiting = this.waitFall.get() && track.body != null;
		this.effects.add(effect);
	}
	// ------------------------------------------------------------------ тело убитого
	/**
	 * Ваниль просит убрать сущность — придержать её до конца анимации?
	 *
	 * <p>Зовётся из миксина на клиентском мире. Сервер убирает тело через двадцать
	 * тиков после смерти, а эффект живёт дольше. Пока он не догорел, удаление
	 * откладывается, и тело лежит там, где легло.</p>
	 */
	public static boolean holdRemoval(int entityId) {
		KillEffect self = instance;
		if (self == null || self.releasing || !self.isEnabled() || !self.holdBody.get()) {
			return false;
		}
		boolean hold = false;
		for (Effect effect : self.effects) {
			if (effect.body != null && effect.body.getId() == entityId) {
				// Помним, что удаление отменили именно мы: доводить до конца надо
				// только своё дело, чужие сущности трогать нельзя.
				effect.held = true;
				hold = true;
			}
		}
		return hold;
	}
	/**
	 * Цвет задержанного тела в ARGB или {@code 0} — рисовать как обычно.
	 *
	 * <p>Зовётся из миксина на рендере живых. Пока мечи машут и торчат, возвращает
	 * ноль: тело обычное и непрозрачное. На исчезновении отдаёт альфу по той же
	 * кривой, по которой тают клинки — поэтому тело и мечи гаснут водин.</p>
	 */
	public static int corpseTint(LivingEntity entity) {
		KillEffect self = instance;
		if (self == null || entity == null || !self.isEnabled() || !self.holdBody.get()) {
			return 0;
		}
		if (self.effects.isEmpty()) {
			return 0;
		}
		long now = System.nanoTime();
		for (Effect effect : self.effects) {
			if (effect.body != entity) {
				continue;
			}
			float alpha = self.corpseAlpha(effect, now);
			if (alpha >= 0.999F) {
				return 0;
			}
			// Ноль в альфе значил бы «не наше тело», поэтому дно чуть выше нуля.
			int level = Math.max(Math.round(alpha * 255.0F), 3);
			return (level << 24) | 0x00FFFFFF;
		}
		return 0;
	}
	/** Начало отрисовки одной сущности: цвет взят из состояния отрисовки. */
	public static void beginSubmit(int tint) {
		submitTint = tint;
	}
	public static void endSubmit() {
		submitTint = 0;
	}
	/**
	 * Наше ли это задержанное тело.
	 *
	 * <p>Нужно отрисовке: ваниль держит красную подсветку урона всё время,
	 * пока счётчик смерти больше нуля. Обычно это секунда и её никто не
	 * замечает, но мы тело держим намного дольше, и труп всю анимацию
	 * остаётся алым. Своим телам эту подсветку снимаем.</p>
	 */
	public static boolean isCorpse(LivingEntity entity) {
		KillEffect self = instance;
		if (self == null || entity == null || !self.isEnabled() || !self.holdBody.get()) {
			return false;
		}
		for (Effect effect : self.effects) {
			if (effect.body == entity) {
				return true;
			}
		}
		return false;
	}
	/** Подмена базового цвета модели внутри {@code submit}. */
	public static int tintCorpse(int vanillaBase) {
		return submitTint == 0 ? vanillaBase : submitTint;
	}
	/**
	 * Насколько плотно видно тело: единица — как обычно, ноль — растворилось.
	 *
	 * <p>Кривая та же, что у мечей, именно поэтому тело и клинки исчезают одновременно,
	 * а не по отдельным таймерам.</p>
	 */
	private float corpseAlpha(Effect effect, long now) {
		float impact = STAB_TRAVEL_SEC + STAB_RAISE_SEC + STAB_DROP_SEC;
		float total = effect.totalSeconds();
		float rest = MathUtil.clamp01(
				(stabPaced(effect, now) - impact) / Math.max(total - impact, 0.2F));
		return rest <= CORPSE_MELT
				? 1.0F
				: 1.0F - MathUtil.smoothStep((rest - CORPSE_MELT) / (1.0F - CORPSE_MELT));
	}
	/**
	 * Отпускает тело: доводит до конца то удаление, которое сами же и отменили.
	 *
	 * <p>Сначала проверка {@code held}: если ваниль тело вообще не просила убирать
	 * (например, эвристика ошиблась и цель жива), убирать её нельзя ни в коем случае:
	 * живая сущность исчезла бы с клиента до перезахода.</p>
	 */
	private void releaseBody(Effect effect) {
		LivingEntity body = effect.body;
		if (!effect.held || body == null || body.isRemoved() || this.level == null) {
			return;
		}
		effect.held = false;
		this.releasing = true;
		try {
			this.level.removeEntity(body.getId(), Entity.RemovalReason.DISCARDED);
		} finally {
			this.releasing = false;
		}
	}
	/**
	 * Тело уже упало и улеглось — пора начинать?
	 *
	 * <p>Ждём двух вещей сразу: чтобы высота перестала меняться — это конец
	 * полёта от удара, — и чтобы дошла ванильная заваливка на бок. Одного
	 * покоя мало: убитый на земле покоен с первого же тика, а валиться будет
	 * ещё секунду, и мечи вошли бы в стоящую тушу.</p>
	 *
	 * <p>Ждать бесконечно нельзя: тело может качаться на волнах, ехать по льду
	 * или лететь в бездну. По истечении {@link #WAIT_MAX_TICKS} начинаем в любом случае.</p>
	 */
	private boolean settled(Effect effect) {
		effect.waitTicks++;
		if (effect.waitTicks >= WAIT_MAX_TICKS) {
			return true;
		}
		LivingEntity target = effect.body;
		// Тела уже нет: на большинстве серверов игрок пропадает вместе с последним
		// ударом. Ждать нечего — бьём по последней известной точке.
		if (target == null || target.isRemoved()) {
			return true;
		}
		double y = target.getY();
		boolean still = Math.abs(y - effect.lastY) < 0.015;
		effect.lastY = y;
		effect.stillTicks = still ? effect.stillTicks + 1 : 0;
		return effect.stillTicks >= 2 && effect.waitTicks >= FALL_LEAN_TICKS;
	}
	/**
	 * Держит тело там, где оно улеглось.
	 *
	 * <p>Труп на клиенте продолжает жить своей жизнью: его доталкивает инерция от
	 * удара, несёт течением, везёт по льду, а игрок может влезть в него и
	 * сдвинуть. Мечи в это время стоят там, куда воткнулись, и тело уезжает
	 * из-под них. Пока идёт анимация, каждый тик возвращаем его на место и гасим
	 * любую скорость.</p>
	 */
	private void pinBody(Effect effect) {
		if (!this.freezeBody.get() || !effect.pinned) {
			return;
		}
		LivingEntity target = effect.body;
		if (target == null || target.isRemoved()) {
			return;
		}
		// Скорость и точка: тело никуда не едет.
		target.setDeltaMovement(0.0, 0.0, 0.0);
		target.setPos(effect.pinX, effect.pinY, effect.pinZ);
		// Одной точки мало: вокруг неё труп всё равно крутится. Толчок сдвигает
		// его внутри тика, от этого сдвига ваниль доворачивает корпус по направлению
		// движения, и угол остаётся, даже когда мы вернём координаты на место.
		target.setYRot(effect.pinYRot);
		target.setXRot(effect.pinXRot);
		target.setYBodyRot(effect.pinBodyRot);
		target.setYHeadRot(effect.pinHeadRot);
		// Прошлые значения тоже наши: кадр рисует не текущее состояние, а плавный
		// переход между тиками. Без этого труп будет дёргаться на каждый толчок.
		target.xo = effect.pinX;
		target.yo = effect.pinY;
		target.zo = effect.pinZ;
		target.yRotO = effect.pinYRot;
		target.xRotO = effect.pinXRot;
		target.yBodyRotO = effect.pinBodyRot;
		target.yHeadRotO = effect.pinHeadRot;
		// И само тело больше ни с чем не взаимодействует: ни блоки под ним,
		// ни тяга вниз на него уже не действуют.
		target.noPhysics = true;
		target.setNoGravity(true);
	}
	/**
	 * Свист появления одного клинка.
	 *
	 * <p>Звук идёт от тела, а не от игрока: эффект стоит на трупе, а звук, звучащий
	 * в голове, разъезжается с картинкой, стоит отойти на пару шагов.</p>
	 *
	 * <p>Высота тона у каждого клинка своя, и берётся она из того же жребия, что
	 * сдвигает его во времени: три одинаковых свиста подряд слышны как один
	 * заезженный сэмпл, а не как три меча.</p>
	 */
	private void playAppear(ClientLevel currentLevel, Effect effect, int index) {
		if (!this.sound.get()) {
			return;
		}
		// 1.4 было слишком высоко: ванильный свист на таком тоне звучит дешёво.
		float pitch = 1.06F + effect.jitterTime[index] * 1.6F;
		currentLevel.playLocalSound(effect.aimX, effect.y + effect.height * 0.5, effect.aimZ,
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3F, pitch, false);
	}
	/**
	 * Глухой удар в момент, когда клинок сел в тело.
	 *
	 * <p>Раньше попадание было вообще без звука: весь звук уходил на один свист в
	 * самом начале, то есть громче всего звучало то, чего ещё не видно.</p>
	 */
	private void playImpact(ClientLevel currentLevel, Effect effect, int index) {
		if (!this.sound.get()) {
			return;
		}
		float pitch = 0.78F + effect.jitterTime[index] * 1.2F;
		currentLevel.playLocalSound(effect.aimX, effect.y + effect.height * 0.5, effect.aimZ,
				SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.45F, pitch, false);
	}
	/** Досчитывает эффекты: искры в момент удара и снятие догоревших. */
	private void tickEffects(ClientLevel currentLevel) {
		if (this.effects.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		for (Effect effect : this.effects) {
			// Пока тело несёт от удара, эффект стоит на паузе: мечи, летящие
			// по воздуху вместе с трупом, сразу выдают подделку.
			if (effect.waiting) {
				if (!this.settled(effect)) {
					// Отсчёт держим на нуле, иначе эффект догорит, ни разу не показавшись.
					effect.start = now;
					continue;
				}
				effect.waiting = false;
				effect.start = now;
				// Тело улеглось: с этого мгновения оно прибито и больше никуда не едет.
				effect.pin();
			}
			this.pinBody(effect);
			float progress = effect.progress(now);
			if (progress >= 1.0F) {
				// Анимация догорела: тело уже растворилось, теперь его можно убрать.
				this.releaseBody(effect);
				this.effects.remove(effect);
				continue;
			}
			if (!this.particles.get()) {
				continue;
			}
			// Попадание у каждого клинка своё, поэтому и искры сыпятся вразнобой: один
			// общий щелчок на три меча и звучит, и выглядит как механизм.
			float stabImpact = STAB_TRAVEL_SEC + STAB_RAISE_SEC + STAB_DROP_SEC;
			for (int index = 0; index < effect.bursts.length; index++) {
				// Свист идёт по каждому клинку в его собственный момент. Один общий свист
				// на весь залп не совпадал ни с одним из трёх появлений.
				if (!effect.appeared[index]
						&& stabTime(effect, index, now) >= stabBirth(index, effect.bursts.length)) {
					effect.appeared[index] = true;
					this.playAppear(currentLevel, effect, index);
				}
				boolean landed = stabTime(effect, index, now) >= stabImpact;
				if (effect.bursts[index] || !landed) {
					continue;
				}
				// Искры ставим один раз на меч: сброс флага дал бы фонтан на каждый тик удара.
				effect.bursts[index] = true;
				this.spawnSparks(currentLevel, effect);
				this.playImpact(currentLevel, effect, index);
			}
		}
	}
	private void spawnSparks(ClientLevel currentLevel, Effect effect) {
		double centerY = effect.y + effect.height * 0.5;
		// Генератор берём свой, а не мира: у {@code Level} он закрыт, а разброс искр — чисто
		// клиентская косметика, синхронность с сервером ей ни к чему.
		for (int index = 0; index < 5; index++) {
			double spreadX = MathUtil.random(-0.35, 0.35);
			double spreadY = MathUtil.random(-0.30, 0.30);
			double spreadZ = MathUtil.random(-0.35, 0.35);
			currentLevel.addParticle(ParticleTypes.CRIT,
					effect.aimX + spreadX, centerY + spreadY, effect.aimZ + spreadZ,
					spreadX * 0.4, 0.05, spreadZ * 0.4);
		}
		currentLevel.addParticle(ParticleTypes.ENCHANTED_HIT,
				effect.aimX, centerY, effect.aimZ, 0.0, 0.0, 0.0);
	}
	// ------------------------------------------------------------------ отрисовка
	/** Вызывается каждый кадр, в том числе с выключенным модулем — отписаться нельзя. */
	private void extract(Render3D.Frame frame) {
		if (!this.isEnabled() || this.effects.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		// Колющий строит веер по линии взгляда, поэтому ему нужен сам смотрящий.
		var viewer = this.mc().player;
		int base = this.baseColor();
		float size = this.scale.getAsFloat();
		boolean ignoreWalls = this.throughWalls.get();
		for (Effect effect : this.effects) {
			// Эффект на паузе, пока тело падает: рисовать пока нечего.
			if (effect.waiting) {
				continue;
			}
			float progress = effect.progress(now);
			// Снимает эффекты тик, а не кадр: кадр не должен менять то, по чему идёт.
			if (progress >= 1.0F) {
				continue;
			}
			int count = effect.bursts.length;
			// Куда от цели смотрит игрок. Веер колющего разворачивается именно сюда:
			// показывать проход мечей за спиной цели бессмысленно.
			// Мечи стоят в теле, а не в точке смерти: пока труп есть — тянемся за ним.
			// С задержкой тела это нужно всем режимам, а не одному колющему.
			effect.follow();
			// Позицию смотрящего берём интерполированной. Сырая обновляется
			// двадцать раз в секунду, а кадров втрое-впятеро больше: веер шёл
			// ступеньками по тику, и на близкой дистанции это видно как тряска.
			Vec3 eye = MathUtil.interpolate(viewer);
			float faceYaw = viewer == null
					? effect.baseYaw
					: effect.smoothYaw(
					(float) Math.toDegrees(
							Math.atan2(eye.x - effect.aimX, eye.z - effect.aimZ)),
					now);
			// Пока клинки летят, веер держится лицом к игроку — иначе заход можно
			// просто не увидеть. Но как только они вошли в тело — они в теле и остаются:
			// воткнутый меч не ездит по кругу вслед за каждым шагом смотрящего.
			if (!effect.yawLocked
					&& stabPaced(effect, now)
					>= STAB_TRAVEL_SEC + STAB_RAISE_SEC + STAB_DROP_SEC + JITTER_TIME) {
				effect.lockedYaw = faceYaw;
				effect.yawLocked = true;
			}
			if (effect.yawLocked) {
				faceYaw = effect.lockedYaw;
			}
			for (int index = 0; index < count; index++) {
				stabPose(stabTime(effect, index, now), effect.totalSeconds(), index, count, this.pose);
				// Меч, который ведущий ещё не обронил, пока не существует.
				if (this.pose.alpha <= 0.0F) {
					continue;
				}
				// baseYaw у эффекта случайный — для веера он мешает, поэтому гасим его
				// вычитанием: drawSword всё равно прибавит его обратно.
				// Ширина веера и его разворот тоже свои каждый раз: без этого три
				// клинка встают одной и той же симметричной вилкой лицом к игроку.
				float slot = faceYaw - effect.baseYaw
						+ stabSlot(index, count) * effect.fanSpread
						+ effect.fanTwist;
				// Расстановка не по линейке: у каждого клинка свой угол, и на каждое
				// добивание он новый.
				slot += effect.jitterYaw[index];
				// Насколько меч уже сел в тело: 0 — ещё идёт по дуге, 1 — воткнут.
				float lead = STAB_TRAVEL_SEC + STAB_RAISE_SEC;
				float settle = MathUtil.clamp01(
						(stabTime(effect, index, now) - lead) / STAB_DROP_SEC);
				this.drawSword(frame, effect, index, slot, settle, base, size, ignoreWalls);
			}
		}
	}
	/** Рисует один меч в позе {@link #pose}: сам клинок, ореол и шлейф. */
	private void drawSword(
			Render3D.Frame frame,
			Effect effect,
			int index,
			float yawOffset,
			float settle,
			int base,
			float size,
			boolean ignoreWalls) {
		float yaw = effect.baseYaw + yawOffset + this.pose.yaw;
		// Наклон, крен и длина у каждого свои. Воткнутый клинок никогда не стоит
		// ровно по отвесу, а три одинаковых вертикали выдают шаблон сразу.
		float swing = this.pose.swing + effect.jitterSwing[index];
		float roll = this.pose.roll + effect.jitterRoll[index];
		float lean = effect.jitterLean[index];
		float blade = size * effect.jitterSize[index] * effect.bodyScale * this.pose.grow;
		double ringRadius;
		double gripY;
		double cutY = Double.NEGATIVE_INFINITY;
		// Колющий целится в саму тушу, а не в отвлечённую точку смерти. У курицы,
		// игрока и голема разная ширина и разная толщина лежащего тела,
		// поэтому и кольцо, и высота обязаны считаться от них.
		// Кольцо считаем по большему габариту лежачего тела. Труп лежит плашмя,
		// и в длину он занимает больше, чем в ширину, а кольцо от одной ширины
		// ставило все три клинка вплотную друг к другу.
		double spread = Math.max(effect.width,
				Math.max(effect.height * 0.5, STAB_MIN_SPREAD));
		ringRadius = spread * STAB_SPREAD * this.radius.get() * this.pose.radius
				* effect.jitterRadius[index];
		// Труп лежит на боку, поэтому его высота — это меньший габарит, а не рост
		// стоящей цели. Целимся в середину этой толщины.
		double thick = Math.min(effect.width, effect.height);
		double lying = Math.min(thick * STAB_LYING, STAB_LYING_MAX);
		// Свой уровень посадки у каждого клинка. Знак сдвига общий для острия
		// и для плоскости входа, но по плоскости он идёт вдвое слабее: так
		// меняется и высота выхода из тела, и глубина посадки.
		double seat = lying * effect.jitterSeat[index];
		// Где остриё висит до снижения и где оно встанет в итоге. Итог
		// берём по наибольшей глубине: от толщины тела или от
		// гарантированного укуса в землю. Второе нужно потому, что у
		// тонкой туши первое даёт остриё выше травы. Поправка на
		// jitterLift здесь обязательна: он сдвигает рукоять, а вместе с
		// ней и остриё, уже после этого расчёта.
		double rest = effect.y + lying * this.height.get() * 0.9;
		double bite = effect.y
				- STAB_GROUND * blade * (1.0 + effect.jitterSeat[index])
				- effect.jitterLift[index] * blade;
		double deep = Math.min(rest - lying * STAB_SINK + seat, bite);
		double aim = rest + (deep - rest) * settle;
		// Плоскость входа: выше неё клинок идёт сквозь стены, ниже — с
		// проверкой глубины, и погружённую часть закрывает само тело
		// или земля. Включаем с самого начала снижения: остриё уходит
		// под плоскость уже на первой четверти пути.
		if (settle > 0.02F) {
			cutY = effect.y + lying * STAB_ENTRY + seat * 0.5;
		}
		// Рукоять ставится так, чтобы остриё пришло ровно в точку прицела, а
		// поза поднимала меч над ней: у воткнутого клинка подъём нулевой.
		double reach = SWORD_TIP * blade * Math.cos(Math.toRadians(swing - 180.0));
		gripY = aim + reach + (this.pose.lift + 1.05) * 0.45 * blade
				+ effect.jitterLift[index] * blade;
		// Куда встанет рукоять. До удара мечи идут по кольцу — так виден заход.
		// К касанию они переезжают на линию вдоль лежачего тела: труп вытянут,
		// и воткнутые клинки должны идти по нему, а не окружать его хороводом.
		int slots = effect.bursts.length;
		double lineStep = Math.max(effect.height, 0.6) * BODY_STEP;
		double along = (index - (slots - 1) * 0.5) * lineStep;
		double across = (effect.jitterRadius[index] - 1.0) * lineStep;
		double lineX = effect.axisX * along - effect.axisZ * across;
		double lineZ = effect.axisZ * along + effect.axisX * across;
		double ringX = Math.sin(Math.toRadians(yaw)) * ringRadius;
		double ringZ = Math.cos(Math.toRadians(yaw)) * ringRadius;
		double slotX = ringX + (lineX - ringX) * settle;
		double slotZ = ringZ + (lineZ - ringZ) * settle;
		int solid = this.withAlpha(base, this.pose.alpha);
		this.drawParts(frame, effect, yaw, swing, roll, lean,
				slotX, slotZ, gripY, cutY, blade, solid, true, ignoreWalls);
		if (this.glow.get()) {
			// Ореол — тот же меч чуть крупнее и почти прозрачный, одним цветом. Всегда
			// сквозь стены: иначе он спорит по глубине с самим клинком и мерцает. Запас по
			// размеру маленький: на полтора ореол раздувал силуэт в одну толстую
			// колбасу, а вокруг острия вырастал заметный куб.
			int halo = this.withAlpha(base, this.pose.alpha * 0.16F);
			this.drawParts(frame, effect, yaw, swing, roll, lean,
					slotX, slotZ, gripY, cutY, blade * 1.16F, halo, false, true);
		}
		if (!this.trail.get() || this.pose.trail <= 0.0F) {
			return;
		}
		// Шлейф — копии клинка на уже пройденном пути. Куда именно их сдвигать, решает
		// сама поза: по замаху, вбок по дуге облёта, вверх по линии удара или всё сразу.
		// Историю кадров хранить не нужно: путь известен наперёд.
		for (int step = 1; step <= TRAIL_STEPS; step++) {
			float fade = this.pose.alpha * this.pose.trail * (1.0F - step / (float) (TRAIL_STEPS + 1)) * 0.45F;
			float back = swing + step * this.pose.trailSwing;
			float side = yaw + step * this.pose.trailYaw;
			double above = gripY + step * this.pose.trailLift * 0.45 * blade;
			// Шлейф идёт тем же путём: своё место на кольце и тот же переезд на линию.
			double trailX = Math.sin(Math.toRadians(side)) * ringRadius;
			double trailZ = Math.cos(Math.toRadians(side)) * ringRadius;
			this.drawParts(frame, effect, side, back, roll, lean,
					trailX + (lineX - trailX) * settle,
					trailZ + (lineZ - trailZ) * settle,
					above, cutY, blade * 0.97F,
					this.withAlpha(base, fade), false, ignoreWalls);
		}
	}
	/**
	 * Складывает части меча в кадр.
	 *
	 * <p>Поворот один на весь меч: сначала yaw вокруг вертикали — он ставит меч на своё место
	 * в круге и разворачивает лицом к цели, потом наклон вокруг своей поперечной оси — это и
	 * есть замах и удар. Плюс по локальному Z смотрит от цели, поэтому положительный наклон
	 * отводит клинок назад, а отрицательный роняет его на цель.</p>
	 *
	 * <p>Крен идёт последним и вокруг уже повёрнутого Y, то есть вокруг самого клинка: именно он
	 * решает, идёт меч лезвием вперёд или шлёпает плоскостью. Порядок менять нельзя:
	 * крен до наклона увёл бы саму плоскость замаха. Смещения частей поворачиваются
	 * тем же кватернионом, поэтому крен ничего не разъезжает — меч остаётся собранным.</p>
	 *
	 * <p>Между наклоном и креном идёт боковое заваливание — именно оно
	 * даёт клинкам разные углы стояния: наклон работает в плоскости
	 * замаха, общей для всего залпа, а заваливание — поперёк неё.
	 * Ставить его после крена нельзя: тогда сторона завала зависела бы
	 * от того, каким боком повёрнут меч.</p>
	 */
	private void drawParts(
			Render3D.Frame frame,
			Effect effect,
			float yawDegrees,
			float swingDegrees,
			float rollDegrees,
			float leanDegrees,
			double slotX,
			double slotZ,
			double gripY,
			double cutY,
			float size,
			int tint,
			boolean shaded,
			boolean ignoreWalls) {
		float yawRadians = (float) Math.toRadians(yawDegrees);
		this.rotation.identity()
				.rotationY(yawRadians)
				.rotateX((float) Math.toRadians(swingDegrees))
				.rotateZ((float) Math.toRadians(leanDegrees))
				.rotateY((float) Math.toRadians(rollDegrees));
		double gripX = effect.aimX + slotX;
		double gripZ = effect.aimZ + slotZ;
		// С какой стороны от плоскости клинка находится смотрящий. Локальный X — это
		// нормаль к плоскости меча, поэтому знак скалярного произведения говорит, какая из
		// двух плоскостей повёрнута к глазу. Здесь можно занять this.offset: в цикле он
		// всё равно перезаписывается на каждой части.
		this.offset.set(1.0F, 0.0F, 0.0F);
		this.rotation.transform(this.offset);
		var viewer = this.mc().player;
		double faceSide = 0.0;
		if (viewer != null) {
			// Здесь тот же тиковый шаг был вреднее всего: у самого меча знак
			// мог переключаться туда-сюда каждый тик, и клинок мигал гранями.
			Vec3 eye = MathUtil.interpolate(viewer);
			double eyeY = eye.y + viewer.getEyeY() - viewer.getY();
			faceSide = (eye.x - gripX) * this.offset.x
					+ (eyeY - gripY) * this.offset.y
					+ (eye.z - gripZ) * this.offset.z;
		}
		// Плоскость среза переводим в длину по самому клинку: локальный +Y идёт от
		// рукояти к острию, так что у воткнутого меча он смотрит вниз. Смещения
		// частей по X и Z на два порядка меньше длины, поэтому вклад одной оси
		// достаточен.
		double cutLocal = 0.0;
		boolean cutting = false;
		if (cutY > Double.NEGATIVE_INFINITY) {
			this.offset.set(0.0F, 1.0F, 0.0F);
			this.rotation.transform(this.offset);
			// Клинок остриём вверх резать нечем: он в тело и не идёт.
			if (this.offset.y < -0.05F) {
				cutLocal = (cutY - gripY) / this.offset.y;
				cutting = true;
			}
		}
		for (Part part : SWORD_PARTS) {
			// Ореол и шлейф берут только тело клинка. Раньше они повторяли и рёбра, и
			// кромки, но в масштабе 1.16 копия ребра стоит уже не над ребром, а рядом:
			// получалось второе тёмное пятно, которое ездило по клинку на каждом
			// повороте. Силуэта для ореола достаточно.
			if (!shaded && part.role() != ROLE_STEEL) {
				continue;
			}
			// Ребро рисуется только на той плоскости, что повёрнута к глазу. Мечи идут
			// сквозь стены, то есть без проверки глубины, и дальнее ребро иначе ложится
			// поверх клинка: два тёмных следа вместо одного, и оба разъезжаются с
			// перспективой — именно это выглядело как «тёмное ездит по мечу».
			if (part.offsetX() != 0.0F && faceSide != 0.0
					&& (part.offsetX() > 0.0F) != (faceSide > 0.0)) {
				continue;
			}
			float partY = part.offsetY() * size;
			float partHeight = part.sizeY() * size;
			int color = shaded ? this.partColor(part.role(), tint) : tint;
			if (!cutting) {
				this.emitPart(frame, part, size, gripX, gripY, gripZ,
						partY, partHeight, color, ignoreWalls);
				continue;
			}
			float near = partY - partHeight * 0.5F;
			float far = partY + partHeight * 0.5F;
			// Наружная часть идёт как и раньше — с настройкой «сквозь стены».
			float outer = Math.min(far, (float) cutLocal);
			if (outer - near > 0.001F) {
				this.emitPart(frame, part, size, gripX, gripY, gripZ,
						(near + outer) * 0.5F, outer - near, color, ignoreWalls);
			}
			// Погружённая часть никуда не девается, но идёт с проверкой глубины:
			// её закрывает само тело, а не наш срез. Раньше она просто вырезалась,
			// и под низкими углами меч честно оставался без кончика.
			float inner = Math.max(near, (float) cutLocal);
			if (far - inner > 0.001F) {
				this.emitPart(frame, part, size, gripX, gripY, gripZ,
						(inner + far) * 0.5F, far - inner, color, false);
			}
		}
	}
	/**
	 * Одна часть меча в кадре.
	 *
	 * <p>Вынесено из цикла ради входа в тело: одна и та же часть клинка
	 * может оказаться разом и снаружи, и внутри, а уходить при этом в разные
	 * проходы: сквозь стены и с проверкой глубины соответственно.</p>
	 */
	private void emitPart(
			Render3D.Frame frame,
			Part part,
			float size,
			double gripX,
			double gripY,
			double gripZ,
			float partY,
			float partHeight,
			int color,
			boolean ignoreWalls) {
		this.offset.set(part.offsetX() * size, partY, part.offsetZ() * size);
		this.rotation.transform(this.offset);
		frame.orientedBox(
				gripX + this.offset.x,
				gripY + this.offset.y,
				gripZ + this.offset.z,
				part.sizeX() * size, partHeight, part.sizeZ() * size,
				this.rotation,
				color,
				ignoreWalls);
	}
	// ------------------------------------------------------------------ анимация
	/**
	 * Место меча в веере колющего: нулевой стоит справа от цели, последний — слева.
	 *
	 * <p>Веер всегда одной ширины, сколько бы мечей ни стояло: шесть клинков должны
	 * встать плотнее трёх, а не расползтись на пол-оборота вокруг цели.</p>
	 */
	private static float stabSlot(int index, int count) {
		if (count <= 1) {
			return 0.0F;
		}
		return STAB_ARC * (0.5F - index / (float) (count - 1));
	}
	/**
	 * Время залпа с личным темпом этого добивания.
	 *
	 * <p>До своего попадания клинки идут личным темпом, после — общим. Иначе на
	 * медленном темпе таяние не успевало бы закончиться до конца эффекта, и мечи с
	 * телом гасли бы рывком. Стык непрерывный: в момент своего удара обе ветки дают
	 * одно и то же время, так что подмены кадра не видно.</p>
	 */
	private static float stabPaced(Effect effect, long now) {
		float impact = STAB_TRAVEL_SEC + STAB_RAISE_SEC + STAB_DROP_SEC;
		float seconds = effect.seconds(now);
		float own = impact / effect.pace;
		return seconds <= own ? seconds * effect.pace : seconds + impact - own;
	}
	/**
	 * Личное время отдельного клинка в колющем.
	 *
	 * <p>Один таймер на три клинка — главное, что читается как механика: мечи
	 * выходят, довзводятся и втыкаются одним кадром, будто их дёргает общий вал.
	 * Сдвиг по клинкам ломает именно это, а темп на весь эффект — одинаковость
	 * между добиваниями.</p>
	 *
	 * <p>Сдвигается время, а не константы фаз: фазы стоят в секундах и завязаны на
	 * падение тела, их нельзя править по одной, не сбив попадание в упавший труп.</p>
	 */
	private static float stabTime(Effect effect, int index, long now) {
		return stabPaced(effect, now) - effect.jitterTime[index];
	}
	/**
	 * Колющий: на какой секунде своего времени появляется клинок с этим номером.
	 *
	 * <p>Счёт идёт в секундах, а не в доле дуги, и это важно: дуга идёт с выбегом,
	 * у её начала доля бежит в разы быстрее времени. Пока проявление силуэтов
	 * считалось по доле, ближние к началу выскакивали тем резче — оттуда и бралась
	 * кривизна появления. Здесь доля обратима кубическим корнем — тем же, чем
	 * задана сама кривая выбега.</p>
	 */
	private static float stabBirth(int index, int count) {
		int leader = count - 1;
		if (index >= leader) {
			return 0.0F;
		}
		float dropAt = Math.min(index / (float) leader, 0.999F);
		float flightSec = Math.max(STAB_TRAVEL_SEC - STAB_APPEAR_SEC, 0.05F);
		return STAB_APPEAR_SEC
				+ flightSec * (float) (1.0 - Math.cbrt(1.0 - dropAt));
	}
	/**
	 * Колющий: один меч уходит влево, роняя по пути силуэты, и весь веер бьёт вниз.
	 *
	 * <p>Поза зависит не только от фазы, но и от номера меча: отсчёт общий на весь
	 * залп, мечи обязаны ударить разом, а до удара — стоять там, где их оставил ведущий.</p>
	 *
	 * <p>Ведущий — последний по номеру. Он появляется у правого края веера и идёт к
	 * своему месту слева, проходя чужие места по дороге. Меч с номером {@code index}
	 * включается ровно тогда, когда ведущий проходит его точку, и наливается из
	 * прозрачного силуэта в полный клинок за {@link #STAB_FADE_SEC}. Отсюда и
	 * картинка: сначала один меч, потом след из силуэтов, потом три меча в ряд.</p>
	 */
	private static void stabPose(float seconds, float total, int index, int count, Pose out) {
		int leader = count - 1;
		// Доля облёта, пройденная ведущим, и доля, на которой стоит место этого меча.
		// Заход с выбегом: клинок стартует резко и докатывается к своему месту.
		// Симметричный сглаженный шаг одинаково медленный и на входе, и на выходе —
		// именно это читается как движение по рельсам.
		float flightSec = Math.max(STAB_TRAVEL_SEC - STAB_APPEAR_SEC, 0.05F);
		float flight = MathUtil.clamp01((seconds - STAB_APPEAR_SEC) / flightSec);
		float left = 1.0F - flight;
		float travel = 1.0F - left * left * left;
		// Ведущий виден с самого начала, остальные — с момента, когда он их обронил.
		float born = index == leader
				? MathUtil.smoothStep(MathUtil.clamp01(seconds / STAB_APPEAR_SEC))
				: MathUtil.smoothStep(MathUtil.clamp01(
				(seconds - stabBirth(index, count)) / STAB_FADE_SEC));
		// Клинок проявляется с подрастанием: выскочить сразу в полный размер —
		// то же самое, что появиться рывком, только по длине.
		out.grow = MathUtil.lerp(STAB_GROW, 1.0F, born);
		// Едет только ведущий: остальные стоят на своих местах и добавлять им нечего.
		out.yaw = index == leader ? STAB_ARC * (1.0F - travel) : 0.0F;
		out.roll = 0.0F;
		out.trail = 0.0F;
		out.trailSwing = 0.0F;
		out.trailYaw = 0.0F;
		out.trailLift = 0.0F;
		if (born <= 0.0F) {
			out.alpha = 0.0F;
			out.swing = 0.0F;
			out.radius = 1.0F;
			out.lift = 1.0F;
			return;
		}
		if (seconds < STAB_TRAVEL_SEC) {
			out.alpha = born;
			out.swing = MathUtil.lerp(150.0F, 160.0F, travel);
			out.radius = MathUtil.lerp(1.35F, 1.15F, travel);
			out.lift = MathUtil.lerp(1.05F, 1.25F, travel);
			// Шлейф нужен только ведущему и только вбок: это его собственные копии на
			// уже пройденной дуге. Стоящим мечам тянуть нечего.
			if (index == leader) {
				out.trail = 0.9F;
				out.trailYaw = 7.0F;
			}
			return;
		}
		if (seconds < STAB_TRAVEL_SEC + STAB_RAISE_SEC) {
			float t = MathUtil.smoothStep((seconds - STAB_TRAVEL_SEC) / STAB_RAISE_SEC);
			out.alpha = born;
			out.swing = MathUtil.lerp(160.0F, 170.0F, t);
			out.radius = MathUtil.lerp(1.15F, 1.0F, t);
			out.lift = MathUtil.lerp(1.25F, 1.6F, t);
			return;
		}
		if (seconds < STAB_TRAVEL_SEC + STAB_RAISE_SEC + STAB_DROP_SEC) {
			float raw = (seconds - STAB_TRAVEL_SEC - STAB_RAISE_SEC) / STAB_DROP_SEC;
			// Квадрат: удар должен разгоняться к концу, а не тормозить.
			float t = raw * raw;
			out.alpha = born;
			out.swing = MathUtil.lerp(170.0F, 186.0F, t);
			out.radius = MathUtil.lerp(1.0F, 0.55F, t);
			out.lift = MathUtil.lerp(1.6F, -1.05F, t);
			// Здесь меч идёт вниз, поэтому его копии остаются выше, а не позади по углу.
			out.trail = raw;
			out.trailLift = 0.8F;
			return;
		}
		// Мечи стоят в теле. Остаток эффекта делится надвое: сначала торчат в полную
		// силу, потом медленно растворяются. Отсюда и смысл ползунка длительности в
		// этом режиме: он задаёт не скорость удара, а время жизни клинков в теле.
		float impact = STAB_TRAVEL_SEC + STAB_RAISE_SEC + STAB_DROP_SEC;
		float rest = MathUtil.clamp01((seconds - impact) / Math.max(total - impact, 0.2F));
		float melt = rest <= STAB_HOLD
				? 0.0F
				: MathUtil.smoothStep((rest - STAB_HOLD) / (1.0F - STAB_HOLD));
		// Отдача: клинок дожимает вниз, качается и затухает. Мёртвая фиксация в одной
		// позе с точностью до кадра — второй машинный признак после ровного залпа:
		// металл после удара дрожит и только потом встаёт.
		float since = seconds - impact;
		float ring = (float) (Math.exp(-since * RECOIL_DAMP)
				* Math.sin(since * RECOIL_RATE));
		out.alpha = born * (1.0F - melt);
		out.swing = MathUtil.lerp(186.0F, 184.0F, melt) + ring * RECOIL_SWING;
		out.radius = 0.55F;
		out.lift = MathUtil.lerp(-1.05F, -1.0F, melt) + ring * RECOIL_LIFT;
		// Шлейф гаснет сразу после удара: у воткнутого клинка шлейфа быть не может.
		out.trail = (1.0F - MathUtil.clamp01(rest * 4.0F)) * 0.5F;
		out.trailLift = 0.8F;
	}
	// ------------------------------------------------------------------ цвет
	private int baseColor() {
		if (this.color.is(COLOR_RED)) {
			return 0xFF4A4A;
		}
		if (this.color.is(COLOR_PURPLE)) {
			return 0xB56BFF;
		}
		if (this.color.is(COLOR_GOLD)) {
			return 0xFFC24A;
		}
		if (this.color.is(COLOR_WHITE)) {
			return 0xF2F6FF;
		}
		return 0x5FE8FF;
	}
	/**
	 * Цвет отдельной части меча.
	 *
	 * <p>Одноцветный меч глаз читает как брусок: без разницы тонов не видно ни рукояти, ни
	 * того, какой стороной идёт клинок. Поэтому у каждой роли свой тон, и к нему лишь
	 * подмешивается цвет из настроек: меч остаётся «своего» цвета, но с формой. Одна кромка
	 * светлая, а обух приглушённый — по этой паре и видно, что меч идёт лезвием вперёд.
	 * Почти чёрным остался только дол по центру: если тёмных мест два, глаз не понимает,
	 * какое из них середина клинка.</p>
	 *
	 * @param role одна из констант {@code ROLE_*}
	 * @param tint цвет модуля с уже посчитанной прозрачностью; его альфа сохраняется
	 */
	private int partColor(int role, int tint) {
		float fade = ColorUtil.alpha(tint) / 255.0F;
		int accent = tint & 0x00FFFFFF;
		int tone = switch (role) {
			case ROLE_GRIP -> ColorUtil.mix(TONE_GRIP, accent, 0.10F);
			case ROLE_METAL -> ColorUtil.mix(TONE_METAL, accent, 0.18F);
			case ROLE_DARK -> ColorUtil.mix(TONE_DARK, accent, 0.16F);
			case ROLE_EDGE -> ColorUtil.mix(TONE_EDGE, accent, 0.22F);
			case ROLE_SPINE -> ColorUtil.mix(TONE_SPINE, accent, 0.30F);
			default -> ColorUtil.mix(TONE_STEEL, accent, 0.40F);
		};
		return ColorUtil.withAlpha(tone, fade);
	}
	/** Цвет модуля с прозрачностью {@code fade} от выставленной в настройках. */
	private int withAlpha(int rgb, float fade) {
		int alpha = (int) (this.opacity.get() * MathUtil.clamp01(fade));
		return ColorUtil.rgba(
				ColorUtil.red(rgb), ColorUtil.green(rgb), ColorUtil.blue(rgb), alpha);
	}
	// ------------------------------------------------------------------ данные
	/** Поза меча в кадре: где он и как повёрнут. Множители, а не готовые координаты. */
	private static final class Pose {
		/** Прозрачность 0..1. */
		float alpha;
		/** Наклон клинка в градусах: плюс — назад от цели, минус — на цель. */
		float swing;
		/** Множитель радиуса из настроек. */
		float radius;
		/** Множитель подъёма над точкой хвата. */
		float lift;
		/** Доворот вокруг цели в градусах. */
		float yaw;
		/** Крен вокруг своей же оси клинка в градусах. */
		float roll;
		/** Сила шлейфа 0..1. */
		float trail;
		/**
		 * На сколько градусов каждая копия шлейфа отведена назад по замаху.
		 *
		 * <p>Шлейф должен лежать на уже пройденном пути: сначала вбок по облёту,
		 * а потом строго вниз.</p>
		 */
		float trailSwing;
		/** На сколько градусов каждая копия шлейфа отстаёт по дуге вокруг цели. */
		float trailYaw;
		/** На сколько каждая копия шлейфа выше — в тех же единицах, что и {@link #lift}. */
		float trailLift;
		/** Множитель длины клинка при проявлении: 1 — полный размер. */
		float grow;
	}
	/**
	 * Одна коробка меча: смещение от хвата, размеры и роль в раскраске.
	 *
	 * <p>Смещение есть по всем трём осям. По X оно нужно рёбрам на плоскостях клинка:
	 * они кладутся вплотную к грани снаружи, а не внутрь металла. На поворот меча это
	 * никак не влияет: все смещения крутятся одним и тем же кватернионом вместе с частями.</p>
	 *
	 * @param role одна из констант {@code ROLE_*}
	 */
	private record Part(
			float offsetX,
			float offsetY,
			float offsetZ,
			float sizeX,
			float sizeY,
			float sizeZ,
			int role) {
	}
	/**
	 * Один запущенный эффект.
	 *
	 * <p>Координаты живые, пока труп есть в мире: мечи должны торчать в теле, а не
	 * в точке смерти. Когда клиент убирает труп, координаты замирают на последнем
	 * значении, и мечи остаются там, где тело лежало.</p>
	 */
	private static final class Effect {
		/**
		 * Тело, в которое войдут мечи, или {@code null}, если о нём уже ничего не знаем.
		 *
		 * <p>Ссылка нужна ровно для одного: пока труп есть в мире, мечи стоят в нём.
		 * Тело успевает отъехать от отбрасывания, сорваться с обрыва или проехать по
		 * льду, и мечи, зависшие в воздухе рядом с ним, сразу выдают подделку.</p>
		 */
		private final LivingEntity body;
		/** Удаление этого тела отменили мы, и довести его до конца тоже нам. */
		private boolean held;
		/** Координаты тела: пока труп в мире — текущие, потом последние известные. */
		private double x;
		private double y;
		private double z;
		/** Рост цели: от него считается высота, чтобы курице и голему шло одинаково. */
		private double height;
		/** Ширина цели: от неё считается, насколько широко встанут мечи. */
		private double width;
		/** Куда целятся мечи: центр видимой туши, а не точка у ног. */
		private double aimX;
		private double aimZ;
		/** Ось, вдоль которой вытянуто лежащее тело: по ней встанут клинки. */
		private double axisX = 1.0;
		private double axisZ;
		/** Насколько цель мельче человека — столько же занимают мечи. */
		private float bodyScale = 1.0F;
		/** Где тело прибито на время анимации и прибито ли вообще. */
		private boolean pinned;
		private double pinX;
		private double pinY;
		private double pinZ;
		/** Углы тела в момент падения: труп должен лежать именно так. */
		private float pinYRot;
		private float pinXRot;
		private float pinBodyRot;
		private float pinHeadRot;
		/** Разворот веера, застывший в момент входа клинков в тело. */
		private boolean yawLocked;
		private float lockedYaw;
		/** Сглаженный разворот веера на игрока и отметка кадра, по которой он идёт. */
		private float viewYaw;
		private boolean viewYawSet;
		private long viewStamp;
		/** Начало отсчёта. Пока эффект ждёт падения, оно каждый тик едет вперёд. */
		private long start;
		private final long length;
		private final float baseYaw;
		/** Эффект ждёт, пока тело долетит до земли и завалится на бок. */
		private boolean waiting;
		/** Сколько тиков ждём и сколько из них тело стоит по высоте. */
		private int waitTicks;
		private int stillTicks;
		/** Высота тела на прошлом тике — по ней и видно, что падение кончилось. */
		private double lastY;
		/** Искры на каждый меч — по одному разу. Длина массива же задаёт число мечей. */
		private final boolean[] bursts;
		/**
		 * Личный сдвиг каждого меча: угол, радиус и высота посадки.
		 *
		 * <p>Жребий бросается один раз на весь эффект, а не каждый кадр: иначе
		 * разброс превратится в тряску.</p>
		 */
		private final float[] jitterYaw;
		private final float[] jitterRadius;
		private final float[] jitterLift;
		private final float[] jitterSwing;
		private final float[] jitterRoll;
		private final float[] jitterLean;
		private final float[] jitterSize;
		private final float[] jitterSeat;
		private final float[] jitterTime;
		/** Какой клинок уже отсвистел своё появление. */
		private final boolean[] appeared;
		/** Ширина веера и его общий разворот относительно смотрящего. */
		private final float fanSpread;
		private final float fanTwist;
		/** Личный темп всего залпа: множитель времени, свой на каждое добивание. */
		private final float pace;
		private Effect(
				LivingEntity body,
				double x,
				double y,
				double z,
				double height,
				double width,
				long start,
				long length,
				float baseYaw,
				int count) {
			this.body = body;
			this.x = x;
			this.y = y;
			this.z = z;
			this.height = height;
			this.width = width;
			this.lastY = y;
			this.aimX = x;
			this.aimZ = z;
			this.pinX = x;
			this.pinY = y;
			this.pinZ = z;
			this.start = start;
			this.length = Math.max(length, 1L);
			this.baseYaw = baseYaw;
			this.bursts = new boolean[Math.max(count, 1)];
			int slots = this.bursts.length;
			this.jitterYaw = new float[slots];
			this.jitterRadius = new float[slots];
			this.jitterLift = new float[slots];
			this.jitterSwing = new float[slots];
			this.jitterRoll = new float[slots];
			this.jitterLean = new float[slots];
			this.jitterSize = new float[slots];
			this.jitterSeat = new float[slots];
			this.jitterTime = new float[slots];
			this.appeared = new boolean[slots];
			this.fanSpread = MathUtil.random(1.0F - FAN_SPREAD, 1.0F + FAN_SPREAD);
			this.fanTwist = MathUtil.random(-FAN_TWIST, FAN_TWIST);
			this.pace = MathUtil.random(1.0F - PACE_SPREAD, 1.0F + PACE_SPREAD);
			for (int slot = 0; slot < slots; slot++) {
				this.jitterYaw[slot] = MathUtil.random(-JITTER_YAW, JITTER_YAW);
				this.jitterRadius[slot] = MathUtil.random(1.0F - JITTER_RADIUS, 1.0F + JITTER_RADIUS);
				this.jitterLift[slot] = MathUtil.random(-JITTER_LIFT, JITTER_LIFT);
				this.jitterSwing[slot] = MathUtil.random(-JITTER_SWING, JITTER_SWING);
				this.jitterRoll[slot] = MathUtil.random(-JITTER_ROLL, JITTER_ROLL);
				this.jitterLean[slot] = MathUtil.random(-JITTER_LEAN, JITTER_LEAN);
				this.jitterSize[slot] = MathUtil.random(1.0F - JITTER_SIZE, 1.0F + JITTER_SIZE);
				this.jitterSeat[slot] = MathUtil.random(-JITTER_SEAT, JITTER_SEAT);
				this.jitterTime[slot] = MathUtil.random(-JITTER_TIME, JITTER_TIME);
			}
		}
		private float progress(long now) {
			return (float) ((now - this.start) / (double) this.length);
		}
		/** Сколько секунд прошло с начала эффекта. */
		private float seconds(long now) {
			return (float) ((now - this.start) / 1.0E9);
		}
		/** Вся длина эффекта в секундах — она же ползунок длительности. */
		private float totalSeconds() {
			return (float) (this.length / 1.0E9);
		}
		/** Подтягивает координаты за телом, пока труп ещё есть в мире. */
		private void follow() {
			LivingEntity target = this.body;
			if (target == null || target.isRemoved()) {
				return;
			}
			// Тело тоже берём в кадре, а не в тике: с выключенной фиксацией труп
			// может ещё скользить или допадать, и мечи шли бы за ним рывками.
			Vec3 spot = MathUtil.interpolate(target);
			this.x = spot.x;
			this.y = spot.y;
			this.z = spot.z;
			// Габариты берём наибольшие из виденных: у умирающего игрока ваниль
			// схлопывает хитбокс в кубик, а целиться надо по живому телу.
			this.height = Math.max(this.height, target.getBbHeight());
			this.width = Math.max(this.width, target.getBbWidth());
			// Труп заваливается на бок, а координаты сущности остаются там же, где
			// были ноги. Видимая туша при этом лежит вбок от них, перпендикулярно
			// развороту тела, и её середина уезжает примерно на полроста. Целиться в
			// сами координаты — значит бить в ноги или вовсе рядом с телом.
			double lean = Math.toRadians(target.yBodyRot);
			double shift = this.height * CORPSE_SHIFT;
			this.aimX = this.x + Math.cos(lean) * shift;
			this.aimZ = this.z + Math.sin(lean) * shift;
			// Туда же, куда тело завалилось, оно и вытянуто: это и есть линия,
			// по которой должны встать воткнутые клинки.
			this.axisX = Math.cos(lean);
			this.axisZ = Math.sin(lean);
			this.bodyScale = bodyScale(this.width, this.height);
		}
		/**
		 * Разворот веера на игрока с ограничением скорости.
		 *
		 * <p>Позиция игрока приходит сюда уже интерполированной, но у угла
		 * остаётся вторая беда, чисто геометрическая: вблизи он меняется
		 * рывками даже при ровной ходьбе. Поэтому доводим угол с пределом
		 * по скорости, а не ставим веер в целевой угол одним кадром.</p>
		 */
		private float smoothYaw(float target, long now) {
			if (!this.viewYawSet) {
				this.viewYaw = target;
				this.viewYawSet = true;
				this.viewStamp = now;
				return this.viewYaw;
			}
			float step = MathUtil.clamp(
					(float) ((now - this.viewStamp) / 1.0E9), 0.0F, VIEW_FRAME_MAX);
			this.viewStamp = now;
			float diff = MathUtil.wrapDegrees(target - this.viewYaw);
			float limit = VIEW_YAW_RATE * step;
			this.viewYaw = MathUtil.wrapDegrees(
					this.viewYaw + MathUtil.clamp(diff, -limit, limit));
			return this.viewYaw;
		}
		/** Размер клинков по габаритам цели: меч не должен быть больше туши. */
		private static float bodyScale(double width, double height) {
			double span = Math.max(width, height * 0.55);
			return (float) MathUtil.clamp(span / BODY_REF, BODY_SCALE_MIN, BODY_SCALE_MAX);
		}
		/** Запоминает точку, в которой тело улеглось: дальше держим его ровно тут. */
		private void pin() {
			LivingEntity target = this.body;
			if (target == null || target.isRemoved()) {
				return;
			}
			this.pinX = target.getX();
			this.pinY = target.getY();
			this.pinZ = target.getZ();
			this.pinYRot = target.getYRot();
			this.pinXRot = target.getXRot();
			this.pinBodyRot = target.yBodyRot;
			this.pinHeadRot = target.yHeadRot;
			this.pinned = true;
		}
	}
	/** Что мы помним о сущности с прошлого тика — только для поиска момента смерти. */
	private static final class Track {
		/** Сама сущность: из неё эффект потом тянет координаты падающего тела. */
		private LivingEntity body;
		private float health;
		private float maxHealth;
		private int hurtTime;
		private double x;
		private double y;
		private double z;
		private double height;
		private double width;
		private int seenTick;
		/** Тик нашего последнего удара по ней. Далёкое прошлое — значит, не наша цель. */
		private int attackedTick = Integer.MIN_VALUE / 2;
		/** Эффект по этой сущности уже запускали. */
		private boolean triggered;
		private Track(LivingEntity entity, int tick) {
			this.update(entity, tick);
		}
		private void update(LivingEntity entity, int tick) {
			this.body = entity;
			this.health = entity.getHealth();
			this.maxHealth = Math.max(entity.getMaxHealth(), 1.0F);
			this.hurtTime = entity.hurtTime;
			this.x = entity.getX();
			this.y = entity.getY();
			this.z = entity.getZ();
			this.height = entity.getBbHeight();
			this.width = entity.getBbWidth();
			this.seenTick = tick;
		}
	}
}
