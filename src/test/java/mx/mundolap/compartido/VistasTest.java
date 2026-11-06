package mx.mundolap.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VistasTest {

    @Test
    void escaparNeutralizaEtiquetasYComillas() {
        assertEquals("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;",
                Html.escapar("<script>alert('x')</script> & \""));
        assertEquals("", Html.escapar(null));
    }

    @Test
    void lasPlantillasEscapanLosDatosDelUsuario() {
        String html = Vistas.render("ordenes/detalle", Vistas.datos("folio", "<b>OS-1</b>"));
        assertTrue(html.contains("&lt;b&gt;OS-1&lt;/b&gt;"));
        assertFalse(html.contains("<b>OS-1</b>"));
    }
}
