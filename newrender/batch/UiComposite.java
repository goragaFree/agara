package rich.util.render.batch;

/**
 * Композит отложенных ванильных отрисовок текущего кадра (предметы/иконки):
 * flushRects + GuiRenderer.render + очистка состояния + LateText. Ставится
 * каждый кадр из {@code GameRendererMixin#afterGuiRender} (там живут
 * guiRenderer / guiState / fogRenderer) и используется HUD-проходами вне
 * этого класса — в первую очередь проходом ЧАТА ({@code ChatScreenMixin}),
 * которому внутренности GameRenderer недоступны, — для пер-элементного
 * композита при перекрытии HUD-элементов друг другом.
 *
 * <p>Проход чата инжектится в НАЧАЛО рендера экрана (HEAD): к этому моменту
 * в отложенном состоянии кадра ещё нет ни текста чата, ни плашки ввода,
 * поэтому промежуточный композит складывает на экран только иконки
 * перекрытых HUD-элементов, не трогая z-порядок самого чата.
 */
public final class UiComposite {

    private static Runnable runner;

    private UiComposite() {}

    /** Обновить раннер композита (каждый кадр, из GameRendererMixin). */
    public static void set(Runnable r) {
        runner = r;
    }

    /** Раннер текущего кадра или {@code null}, если ещё не выставлен (самый первый кадр). */
    public static Runnable get() {
        return runner;
    }
}
