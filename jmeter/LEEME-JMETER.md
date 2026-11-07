# Laboratorio JMeter para ChatPrivate

Este plan deja preparada una primera prueba de carga REST para ejecutarla cuando el backend esté iniciado. Usa las rutas que existen en el backend y consulta datos; no crea cuentas, conversaciones ni mensajes.

## Qué mide el plan

1. Inicia sesión una sola vez en `POST /api/auth/login` y obtiene el JWT.
2. Reutiliza el JWT en las consultas de cada usuario virtual.
3. Consulta `GET /api/users/me`, `GET /api/conversations`, los participantes de una conversación y su historial paginado.
4. Revisa que cada petición responda HTTP 200.

El login se ejecuta una sola vez porque el backend limita los intentos por IP. La prueba no estresa el endpoint de autenticación.

## Antes de ejecutarla

- Inicia el backend y confirma que está disponible en `http://localhost:8080`.
- Usa una base de datos local de desarrollo con información de prueba. No apuntes el plan a producción ni a datos reales.
- Ten una cuenta de prueba válida y el ID de una conversación a la que esa cuenta pertenezca.
- Instala Apache JMeter 5.6.3 o una versión compatible.
- No guardes la contraseña ni el token dentro de este archivo.

## Abrir el plan en la aplicación JMeter

En este equipo JMeter está en:

`C:\Users\DAOM\Downloads\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter.bat`

Abre el Explorador de archivos, pega esa carpeta (sin el nombre `jmeter.bat`) en la barra de dirección y abre `jmeter.bat`. Cuando aparezca JMeter:

1. Selecciona **File > Open**.
2. Abre `C:\Users\DAOM\Desktop\APP_tcc_backend\BackendChatPrivate\jmeter\chatprivate-api-load-test.jmx`.
3. En el árbol de la izquierda selecciona el nodo principal **ChatPrivate - prueba inicial de carga API**. En **Variables del plan**, completa `username`, `password` y `conversationId`. La contraseña y los demás valores privados deben permanecer solo en la sesión; no guardes el plan después de escribirlos.
4. Deja `protocol=http`, `host=localhost` y `port=8080`, salvo que el backend esté en otra dirección.
5. Conserva inicialmente `virtualUsers=5`, `rampUpSeconds=10` y `loops=5`. Son hasta 100 solicitudes de lectura, aparte del login único.
6. En el árbol selecciona el plan principal, usa **Add > Listener > Aggregate Report** (o **Summary Report**) y ejecuta con el botón verde. No uses **View Results Tree** durante una corrida de carga.
7. Al terminar, guarda/exporta el resumen sin incluir usuario, contraseña ni token. Anota fecha, versión del código y configuración usada para poder comparar.

La conversación debe existir y pertenecer a la cuenta de prueba; si no, las consultas de participantes o historial devolverán error.

## Qué revisar en el resultado

- **# Samples**: cuántas solicitudes se ejecutaron.
- **Error %**: porcentaje de solicitudes con fallo; para esta primera corrida se espera 0 %.
- **Average / Median / 90% Line / 95% Line / 99% Line**: tiempos de respuesta, en milisegundos.
- **Throughput**: solicitudes atendidas por segundo; sirve para comparar corridas hechas en las mismas condiciones.
- Si aparecen 401, revisa el usuario/contraseña y que el login haya obtenido JWT. Si aparece 403, confirma que la cuenta pertenece a esa conversación. Si aparece 404, revisa el ID. Si hay errores de conexión, comprueba que el backend siga activo.

## Cómo crecer la carga

Primero guarda el resultado de referencia. Luego cambia solo una variable por corrida (por ejemplo, 5 a 10 usuarios y después a 20), mantén la rampa y las iteraciones registradas, y compara errores y percentiles. Detén la prueba si el backend o la base de datos dejan de responder. Esta configuración inicial no determina por sí sola una capacidad máxima ni reemplaza pruebas con infraestructura de despliegue.

## Archivos

- `chatprivate-api-load-test.jmx`: plan de prueba de JMeter.
- `LEEME-JMETER.md`: preparación, ejecución y lectura de resultados.
