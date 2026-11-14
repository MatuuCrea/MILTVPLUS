# Política de producto: MILTVPLUS 100 % gratuita

**Estado:** requisito obligatorio del producto  
**Actualizado:** 2026-11-13

## Compromiso con las personas usuarias

MILTVPLUS se distribuye gratuitamente y no incorpora mecanismos de monetización. La experiencia no debe incluir:

- Publicidad, anuncios, banners patrocinados ni intersticiales.
- Suscripciones, planes premium, períodos de prueba con cobro ni muros de pago.
- Compras dentro de la aplicación, pagos, propinas, donaciones solicitadas ni enlaces de afiliación.
- Funciones esenciales bloqueadas detrás de un pago.
- Mensajes comerciales intrusivos o pantallas promocionales que interrumpan el uso.

No se debe agregar SDK de publicidad, facturación o atribución comercial sin una modificación explícita de esta política aprobada por el propietario. Los controles de edad y seguridad no son mecanismos de monetización.

## Criterios técnicos y de calidad

- No agregar dependencias de Ads, Billing o compras integradas.
- Mantener las claves privadas y tokens exclusivamente en servicios del lado servidor.
- Mostrar errores comprensibles y recuperables; no ocultar fallos ni inventar contenido, disponibilidad o reproducción.
- No romper navegación, identidad visual ni funciones existentes al agregar módulos.
- Probar Android y Windows en CI; documentar lo que todavía requiere pruebas físicas.
- No presentar como gratuito un servicio externo, alojamiento, almacenamiento, ancho de banda o licencia que tenga límites o costos. Si una dependencia deja de ofrecer un nivel gratuito compatible, informar y desactivar esa integración antes que introducir cobros o publicidad en MILTVPLUS.

## Servicios externos y derechos

La gratuidad de MILTVPLUS no concede derechos sobre canales, películas, series, música, imágenes ni streams. Cada proveedor conserva sus condiciones y territorios. Sólo se incorporan fuentes con autorización y procedencia verificable.

### TMDB

El uso de la API de TMDB se limita a un proyecto no comercial mientras esa condición se mantenga. Se debe mostrar el logotipo oficial aprobado de TMDB y, en Acerca de/Créditos, el aviso exacto:

> This product uses the TMDB API but is not endorsed or certified by TMDB.

No se debe activar la integración pública hasta completar la atribución visual requerida y comprobar que la modalidad de uso sigue cumpliendo los términos vigentes de TMDB. TMDB entrega metadatos e imágenes; no otorga derechos de reproducción ni URLs de vídeo.

Referencia oficial: https://developer.themoviedb.org/docs/faq  
Logotipos y atribución: https://www.themoviedb.org/about/logos-attribution  
Términos: https://www.themoviedb.org/api-terms-of-use

## Regla de revisión

Cualquier cambio que introduzca publicidad, pagos, suscripciones, compras, afiliación o monetización contradice esta política y debe rechazarse hasta que el propietario cambie expresamente el requisito.
