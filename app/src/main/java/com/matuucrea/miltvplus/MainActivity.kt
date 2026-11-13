package com.matuucrea.miltvplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matuucrea.miltvplus.data.Channel
import com.matuucrea.miltvplus.data.M3uParser
import com.matuucrea.miltvplus.player.PlayerViewModel

private val bg=Color(0xFF05091B); private val panel=Color(0xFF101A38); private val cyan=Color(0xFF00D9F5); private val blue=Color(0xFF087BFF); private val orange=Color(0xFFFF7900)
class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(5,9,27);window.navigationBarColor=android.graphics.Color.rgb(5,9,27);setContent { MaterialTheme(colorScheme=darkColorScheme(primary=cyan,secondary=orange,background=bg,surface=panel)){ App() } } }
}
@Composable private fun App(vm:PlayerViewModel=viewModel()){
    val context=LocalContext.current
    var tab by remember { mutableStateOf("Inicio") }; var query by remember { mutableStateOf("") }
    val channels by vm.channels.collectAsState(); val selected by vm.selected.collectAsState(); val error by vm.error.collectAsState(); val favorites by vm.favorites.collectAsState()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){ uri ->
        uri?.let { u -> runCatching { context.contentResolver.openInputStream(u)?.bufferedReader()?.use { M3uParser.parse(it.readText()) } ?: error("No se pudo abrir el archivo") }
            .onSuccess { vm.setChannels(it); tab="TV" }.onFailure { vm.setError("No se pudo importar: "+(it.message ?: "archivo inválido")) } }
    }
    Scaffold(containerColor=bg,bottomBar={NavigationBar(containerColor=Color(0xFF080F28)){
        listOf("Inicio","TV","Fuentes","Favoritos","Ajustes").forEach { t ->
            val ico=when(t){"Inicio"->Icons.Default.Home;"TV"->Icons.Default.LiveTv;"Fuentes"->Icons.Default.PlaylistAdd;"Favoritos"->Icons.Default.Favorite;else->Icons.Default.Settings}
            NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Icon(ico,null)},label={Text(t)},colors=NavigationBarItemDefaults.colors(selectedIconColor=cyan,selectedTextColor=cyan,indicatorColor=blue.copy(alpha=.2f)))
        }
    }}){pad->Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF071638),bg,Color(0xFF100D20)))).padding(pad)){
        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("MILTVPLUS",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Black,letterSpacing=1.5.sp);Text("ENTRETENIMIENTO • V22.34.9",color=cyan,fontSize=10.sp)};Text("● LOCAL",color=cyan,fontSize=10.sp)}
        when(tab){
            "Inicio"->{Card(Modifier.fillMaxWidth().padding(horizontal=16.dp),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=panel)){Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF082E73),panel,Color(0xFF451B21)))).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("TU ENTRETENIMIENTO,\nA TU MANERA",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.ExtraBold);Text(channels.size.toString()+" canales en tu biblioteca",color=cyan);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={tab="TV"}){Text("Explorar TV")};OutlinedButton(onClick={tab="Fuentes"}){Text("Agregar fuente")}}}};Title("Tu espacio","Contenido desde fuentes autorizadas");if(channels.isEmpty())Empty("Todavía no agregaste canales","Importá un archivo M3U/M3U8 propio o autorizado.")}
            "Fuentes"->{Title("Fuentes","Importación local controlada");Card(Modifier.fillMaxWidth().padding(16.dp),colors=CardDefaults.cardColors(containerColor=panel)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.PlaylistAdd,null,tint=cyan);Text("Agregar lista M3U / M3U8",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("Seleccioná un archivo del dispositivo. Usá solo listas propias, públicas o licenciadas.",color=Color.LightGray);Button(onClick={picker.launch(arrayOf("audio/x-mpegurl","application/vnd.apple.mpegurl","text/plain","*/*"))}){Icon(Icons.Default.FolderOpen,null);Text("Seleccionar archivo")}}};error?.let{Text(it,Modifier.padding(18.dp),color=orange)}}
            "Ajustes"->{Title("Ajustes","Preferencias y compatibilidad");listOf("Reproducción" to "Motor Media3 · HLS","Privacidad" to "Playlists locales en el dispositivo","Dispositivos" to "Android · Android TV · Google TV","Versión" to "V22.34.9").forEach{(a,b)->Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),colors=CardDefaults.cardColors(containerColor=panel)){Column(Modifier.padding(16.dp)){Text(a,color=Color.White,fontWeight=FontWeight.Bold);Text(b,color=Color.LightGray,fontSize=12.sp)}}}}
            else->{Title(if(tab=="TV")"Televisión en vivo" else "Tus favoritos","Elegí un canal para reproducir");OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp),placeholder={Text("Buscar canal o categoría")},singleLine=true);val list=channels.filter{(tab!="Favoritos"||it.id in favorites)&&(it.name.contains(query,true)||it.group.contains(query,true))};if(list.isEmpty())Empty("No hay canales para mostrar","Agregá una lista autorizada desde Fuentes.");LazyColumn(contentPadding=PaddingValues(14.dp)){items(list,key={it.id}){c->ChannelItem(c,c.id in favorites,{vm.play(c)},{vm.toggleFavorite(c.id)})}};selected?.let{Card(Modifier.fillMaxWidth().padding(14.dp),colors=CardDefaults.cardColors(containerColor=Color.Black)){Column(Modifier.padding(14.dp)){Text("SELECCIONADO · "+it.name,color=cyan);Text("El motor Media3 está incluido; la superficie de reproducción y el lifecycle requieren integración y QA.",color=Color.LightGray,fontSize=12.sp);TextButton(onClick={vm.stop()}){Text("Detener")}}}};error?.let{Text(it,color=orange,modifier=Modifier.padding(12.dp))}}
        }
    }}
}
@Composable private fun Title(a:String,b:String){Column(Modifier.padding(start=20.dp,end=20.dp,top=22.dp,bottom=10.dp)){Text(a,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(b,color=Color.LightGray,fontSize=12.sp)}}
@Composable private fun Empty(a:String,b:String){Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.TvOff,null,tint=cyan,modifier=Modifier.size(36.dp));Text(a,color=Color.White,fontWeight=FontWeight.Bold);Text(b,color=Color.LightGray,fontSize=12.sp)}}
@Composable private fun ChannelItem(c:Channel,f:Boolean,play:()->Unit,fav:()->Unit){Card(onClick=play,Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=panel)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LiveTv,null,tint=cyan);Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(c.name,color=Color.White,fontWeight=FontWeight.SemiBold);Text(c.group.ifBlank{"Sin categoría"},color=Color.LightGray,fontSize=11.sp)};IconButton(onClick=fav){Icon(if(f)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=if(f)orange else Color.LightGray)}}}}
