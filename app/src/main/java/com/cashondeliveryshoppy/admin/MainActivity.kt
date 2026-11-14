package com.cashondeliveryshoppy.admin

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ADMIN_EMAIL = "cashondeliveryshoppy@gmail.com"

// Two blank lines on top for your own description, then the fixed "Link in Bio" lines.
private const val DEFAULT_DESCRIPTION = "\n\nLink in Bio\nhttps://cashondeliveryshoppy.netlify.app/"

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CODAdminApp(auth, db) }
    }
}

data class Product(
    val id: String, val name: String, val price: Double,
    val description: String, val image: String, val images: List<String>, val active: Boolean
)
data class Order(
    val id: String, val productName: String, val qty: Long, val total: Double,
    val name: String, val phone: String, val address: String, val city: String,
    val state: String, val pincode: String, val email: String, val status: String,
    val shippingCompany: String, val shippingId: String, val instructions: String,
    val createdAt: Date?, val selectedImage: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CODAdminApp(auth: FirebaseAuth, db: FirebaseFirestore) {
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(auth.currentUser != null) }
    var currentEmail by remember { mutableStateOf(auth.currentUser?.email.orEmpty()) }
    var tab by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }

    fun loadProducts() {
        db.collection("products").orderBy("createdAt", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { snap ->
                products = snap.documents.map { d ->
                    val main = d.getString("image") ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val list = (d.get("images") as? List<*>)?.filterIsInstance<String>()
                        ?.takeIf { it.isNotEmpty() } ?: if (main.isNotBlank()) listOf(main) else emptyList()
                    Product(d.id, d.getString("name") ?: "", d.getDouble("price") ?: 0.0,
                        d.getString("description") ?: "", main, list,
                        d.getBoolean("active") ?: true)
                }
            }.addOnFailureListener { message = it.localizedMessage ?: "Products load failed" }
    }
    fun loadOrders() {
        db.collection("orders").orderBy("createdAt", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { snap ->
                orders = snap.documents.map { d ->
                    val ts = d.getTimestamp("createdAt")
                    Order(d.id, d.getString("productName") ?: "", d.getLong("qty") ?: 1,
                        d.getDouble("total") ?: 0.0, d.getString("name") ?: "",
                        d.getString("phone") ?: "", d.getString("address") ?: "",
                        d.getString("city") ?: "", d.getString("state") ?: "",
                        d.getString("pincode") ?: "", d.getString("email") ?: "",
                        d.getString("status") ?: "new", d.getString("shippingCompany") ?: "",
                        d.getString("shippingId") ?: "", d.getString("instructions") ?: "",
                        ts?.toDate(), selectedImage = d.getString("selectedImage") ?: "")
                }
            }.addOnFailureListener { message = it.localizedMessage ?: "Orders load failed" }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(com.google.android.gms.common.api.ApiException::class.java)

            val token = account.idToken
            if (token.isNullOrBlank()) {
                message = "Google ID Token is missing. Check Web Client ID."
            } else {
                val credential = GoogleAuthProvider.getCredential(token, null)
                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        message = "Google login successful!"
                    }
                    .addOnFailureListener {
                        message = "Firebase Error: ${it.localizedMessage}"
                    }
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            message = "Google Error Code: ${e.statusCode} - ${e.statusMessage}"
        } catch (e: Exception) {
            message = "Login Error: ${e.localizedMessage}"
        }
    }

    LaunchedEffect(signedIn) {
        if (signedIn && currentEmail.equals(ADMIN_EMAIL, true)) {
            loadProducts()
            loadOrders()
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("COD Shoppy Admin", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (signedIn) IconButton(onClick = {
                            if (tab == 0) loadProducts() else loadOrders()
                        }) { Icon(Icons.Default.Refresh, "Refresh") }
                        if (signedIn) IconButton(onClick = {
                            auth.signOut()
                            signedIn = false
                            currentEmail = ""
                        }) { Icon(Icons.Default.Logout, "Logout") }
                    }
                )
            },
            bottomBar = {
                if (signedIn && currentEmail.equals(ADMIN_EMAIL, true)) {
                    NavigationBar {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Inventory2, null) }, label = { Text("Products") })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.LocalShipping, null) }, label = { Text("Orders") })
                    }
                }
            }
        ) { padding ->
            if (!signedIn) {
                LoginScreen(
                    modifier = Modifier.padding(padding),
                    onLogin = {
                        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                        if (id == 0) {
                            message = "Firebase setup incomplete: add google-services.json to the app folder."
                        } else {
                            val clientId = context.getString(id)
                            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(clientId).requestEmail().build()
                            val client = GoogleSignIn.getClient(context, options)
                            signInLauncher.launch(client.signInIntent)
                        }
                    },
                    message = message
                )
            } else if (!currentEmail.equals(ADMIN_EMAIL, true)) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("This Google account is not the store admin.", color = MaterialTheme.colorScheme.error)
                    Text(currentEmail)
                    Button(onClick = { auth.signOut(); signedIn = false }) { Text("Sign out") }
                }
            } else if (tab == 0) {
                ProductsScreen(
                    modifier = Modifier.padding(padding), products = products, message = message,
                    onAdd = { name, price, desc, images ->
                        busy = true
                        db.collection("products").add(hashMapOf(
                            "name" to name, "price" to price, "description" to desc,
                            "image" to images[0], "images" to images,
                            "active" to true, "createdAt" to FieldValue.serverTimestamp()
                        )).addOnSuccessListener {
                            message = "Product uploaded! It will appear on the customer website."
                            loadProducts(); busy = false
                        }.addOnFailureListener {
                            message = it.localizedMessage ?: "Upload failed"; busy = false
                        }
                    },
                    onUpdate = { id, name, price, desc, images ->
                        busy = true
                        db.collection("products").document(id).update(mapOf(
                            "name" to name, "price" to price, "description" to desc,
                            "image" to images[0], "images" to images
                        )).addOnSuccessListener {
                            message = "Product updated! Changes are live on the customer website."
                            loadProducts(); busy = false
                        }.addOnFailureListener {
                            message = it.localizedMessage ?: "Update failed"; busy = false
                        }
                    },
                    onToggle = { p ->
                        db.collection("products").document(p.id).update("active", !p.active)
                            .addOnSuccessListener { loadProducts() }
                            .addOnFailureListener { message = it.localizedMessage ?: "Update failed" }
                    },
                    onDelete = { p ->
                        db.collection("products").document(p.id).delete()
                            .addOnSuccessListener { loadProducts() }
                            .addOnFailureListener { message = it.localizedMessage ?: "Delete failed" }
                    },
                    busy = busy
                )
            } else {
                OrdersScreen(
                    modifier = Modifier.padding(padding), orders = orders, message = message,
                    onStatus = { order, status ->
                        val changes = hashMapOf<String, Any>("status" to status)
                        if (status == "delivered") changes["deliveredAt"] = FieldValue.serverTimestamp()
                        db.collection("orders").document(order.id).update(changes)
                            .addOnSuccessListener { message = "Order status updated."; loadOrders() }
                            .addOnFailureListener { message = it.localizedMessage ?: "Update failed" }
                    },
                    onShip = { order, company, tracking ->
                        if (company.isBlank() || tracking.isBlank()) {
                            message = "Enter both courier company and tracking ID."
                        } else {
                            db.collection("orders").document(order.id).update(
                                mapOf("shippingCompany" to company, "shippingId" to tracking,
                                    "status" to "shipped", "shippedAt" to FieldValue.serverTimestamp())
                            ).addOnSuccessListener { message = "Order marked as shipped."; loadOrders() }
                                .addOnFailureListener { message = it.localizedMessage ?: "Shipping update failed" }
                        }
                    }
                )
            }
        }
    }

    // Observe Firebase sign-in changes including the Google account result.
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            signedIn = firebase.currentUser != null
            currentEmail = firebase.currentUser?.email.orEmpty()
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
}

@Composable
fun LoginScreen(modifier: Modifier = Modifier, onLogin: () -> Unit, message: String) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🛍️", style = MaterialTheme.typography.displayLarge)
        Text("COD Shoppy Admin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Sign in with your store Google account to manage products and orders.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    modifier: Modifier, products: List<Product>, message: String,
    onAdd: (String, Double, String, List<String>) -> Unit,
    onUpdate: (String, String, Double, String, List<String>) -> Unit,
    onToggle: (Product) -> Unit, onDelete: (Product) -> Unit, busy: Boolean
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    // New products start with the "Link in Bio" lines already in the box; the cursor sits at the top.
    var descField by remember { mutableStateOf(TextFieldValue(DEFAULT_DESCRIPTION, TextRange(0))) }
    val description = descField.text
    var editing by remember { mutableStateOf<Product?>(null) }
    val maxPhotos = 5
    val minPhotos = if (editing != null) 1 else 3
    var photos by remember { mutableStateOf<List<Pair<String, Bitmap>>>(emptyList()) }
    var localMsg by remember { mutableStateOf("") }
    // Size (w, h) of the first cropped photo; every other photo is cropped to this same size.
    var refSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pending by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cropping by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun resetForm() {
        name = ""; price = ""; descField = TextFieldValue(DEFAULT_DESCRIPTION, TextRange(0))
        photos = emptyList(); localMsg = ""; editing = null
        refSize = null; pending = emptyList()
    }

    fun startEdit(p: Product) {
        editing = p
        name = p.name
        price = if (p.price % 1.0 == 0.0) p.price.toLong().toString() else p.price.toString()
        descField = TextFieldValue(p.description)
        photos = p.images.mapNotNull { url -> dataUrlToBitmap(url)?.let { url to it } }
        refSize = photos.firstOrNull()?.second?.let { it.width to it.height }
        localMsg = if (photos.size < p.images.size)
            "Some old photos couldn't be loaded; add them again if needed." else ""
        scope.launch { listState.animateScrollToItem(0) }
    }

    // Result of the crop screen for one photo.
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val croppedUri = result.uriContent
        if (result.isSuccessful && croppedUri != null) {
            try {
                var bitmap = decodeAndResize(context, croppedUri)
                val ref = refSize
                if (ref == null) {
                    // First photo: the size the user chose becomes the size for all photos.
                    refSize = bitmap.width to bitmap.height
                } else if (bitmap.width != ref.first || bitmap.height != ref.second) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, ref.first, ref.second, true)
                }
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 65, output)
                val data = "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                photos = photos + (data to bitmap)
            } catch (e: Exception) {
                localMsg = "Could not process one of the photos."
            }
        }
        pending = pending.drop(1)
        cropping = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val room = maxPhotos - photos.size
            pending = uris.take(room)
            localMsg = if (uris.size > room) "Maximum $maxPhotos photos allowed. Extra photos were skipped." else ""
        }
    }

    // Opens the crop screen for each picked photo, one after another.
    LaunchedEffect(pending, cropping) {
        val next = pending.firstOrNull()
        if (next != null && !cropping) {
            cropping = true
            val ref = refSize
            val options = if (ref == null) {
                CropImageOptions(guidelines = CropImageView.Guidelines.ON)
            } else {
                CropImageOptions(
                    guidelines = CropImageView.Guidelines.ON,
                    fixAspectRatio = true, aspectRatioX = ref.first, aspectRatioY = ref.second
                )
            }
            cropLauncher.launch(CropImageContractOptions(next, options))
        }
    }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (editing != null) "Edit product" else "Add new product",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, label = { Text("Product name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(price, { price = it }, label = { Text("Price (₹) *") },
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(descField, { descField = it }, label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(), minLines = 4)
                    Text(
                        refSize?.let { "Photo size locked: ${it.first} × ${it.second}px. Every new photo is cropped to this size. Remove all photos to choose a new size." }
                            ?: "Crop your first photo to any size you want. All other photos will be cropped to that same size.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { picker.launch("image/*") },
                        enabled = photos.size < maxPhotos && pending.isEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp))
                        Text("Choose photos from Gallery (min $minPhotos, max $maxPhotos)")
                    }
                    Text(
                        "${photos.size} / $maxPhotos photos added",
                        color = if (photos.size >= minPhotos) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (photos.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            photos.forEachIndexed { i, pair ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(pair.second.asImageBitmap(), null, Modifier.size(96.dp))
                                    Text(if (i == 0) "Main" else "Photo ${i + 1}", style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = {
                                        photos = photos.filterIndexed { idx, _ -> idx != i }
                                        if (photos.isEmpty()) refSize = null
                                    }) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    if (localMsg.isNotBlank()) Text(localMsg, color = MaterialTheme.colorScheme.error)
                    Button(
                        enabled = !busy && name.isNotBlank() && (price.toDoubleOrNull() ?: 0.0) > 0 && photos.size >= minPhotos,
                        onClick = {
                            val images = photos.map { it.first }
                            if (images.sumOf { it.length } > 800000) {
                                localMsg = "Photos are too large together. Please remove one or use smaller photos."
                            } else {
                                val current = editing
                                if (current != null) {
                                    onUpdate(current.id, name.trim(), price.toDouble(), description.trim(), images)
                                } else {
                                    onAdd(name.trim(), price.toDouble(), description.trim(), images)
                                }
                                resetForm()
                            }
                        }, modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                busy -> if (editing != null) "Updating…" else "Uploading…"
                                editing != null -> "UPDATE PRODUCT"
                                else -> "UPLOAD PRODUCT"
                            }
                        )
                    }
                    if (editing != null) {
                        OutlinedButton(onClick = { resetForm() }, modifier = Modifier.fillMaxWidth()) {
                            Text("CANCEL EDIT")
                        }
                    }
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Text("Your products", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (products.isEmpty()) item { Text("No products found yet.") }
        items(products, key = { it.id }) { p ->
            ElevatedCard {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("₹${"%.2f".format(p.price)}")
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (p.active) "Visible on website" else "Hidden")
                    Row {
                        TextButton(onClick = { startEdit(p) }) { Text("Edit") }
                        TextButton(onClick = { onToggle(p) }) { Text(if (p.active) "Hide" else "Show") }
                        TextButton(onClick = { onDelete(p) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
                OutlinedButton(
                    onClick = { shareToInstagram(context, p) },
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                ) { Text("📸 Share to Instagram") }
            }
        }
    }
}

private fun statusColor(status: String): Color = when (status) {
    "new" -> Color(0xFF1E88E5)
    "confirmed" -> Color(0xFF8E24AA)
    "shipped" -> Color(0xFF00897B)
    "out_for_delivery" -> Color(0xFFEF6C00)
    "delivered" -> Color(0xFF2E7D32)
    "cancelled" -> Color(0xFFC62828)
    else -> Color(0xFF616161)
}

private fun statusLabel(status: String): String =
    status.replace("_", " ").replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    modifier: Modifier, orders: List<Order>, message: String,
    onStatus: (Order, String) -> Unit, onShip: (Order, String, String) -> Unit
) {
    var filter by remember { mutableStateOf("all") }
    var openId by remember { mutableStateOf<String?>(null) }
    val filters = listOf("all", "new", "confirmed", "shipped", "out_for_delivery", "delivered", "cancelled")
    val shown = if (filter == "all") orders else orders.filter { it.status == filter }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.primary) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { f ->
                    val count = if (f == "all") orders.size else orders.count { it.status == f }
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(if (f == "all") "All ($count)" else "${statusLabel(f)} ($count)") }
                    )
                }
            }
        }
        if (shown.isEmpty()) item { Text("No orders here.") }
        items(shown, key = { it.id }) { o ->
            val open = openId == o.id
            var company by remember(o.id) { mutableStateOf(o.shippingCompany) }
            var tracking by remember(o.id) { mutableStateOf(o.shippingId) }
            var expanded by remember(o.id) { mutableStateOf(false) }
            ElevatedCard(Modifier.fillMaxWidth().clickable { openId = if (open) null else o.id }) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Short summary, always visible. Tap the card (or the Order ID) to open details.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val thumb = remember(o.selectedImage) { dataUrlToBitmap(o.selectedImage) }
                        if (thumb != null) {
                            Image(thumb.asImageBitmap(), null,
                                Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Order #${o.id.take(6).uppercase()}",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(o.productName + " × " + o.qty)
                            Text("${o.name}  •  ${o.city}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(shape = RoundedCornerShape(50), color = statusColor(o.status).copy(alpha = 0.15f)) {
                                Text(statusLabel(o.status), color = statusColor(o.status),
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("₹" + "%.2f".format(o.total), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    o.createdAt?.let {
                        Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (open) {
                        HorizontalDivider()
                        dataUrlToBitmap(o.selectedImage)?.let {
                            Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 280.dp))
                        }
                        Text("Order ID: ${o.id}", style = MaterialTheme.typography.bodySmall)
                        Text("Customer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("${o.name}  •  ${o.phone}")
                        Text("${o.address}, ${o.city}, ${o.state} - ${o.pincode}")
                        if (o.email.isNotBlank()) Text(o.email, style = MaterialTheme.typography.bodySmall)
                        if (o.instructions.isNotBlank()) Text("Instructions: ${o.instructions}")
                        Text("COD Total: ₹${o.total}", fontWeight = FontWeight.SemiBold)
                        HorizontalDivider()
                        Text("Shipping", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(company, { company = it }, label = { Text("Courier company") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(tracking, { tracking = it }, label = { Text("Tracking ID") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { onShip(o, company, tracking) }, modifier = Modifier.fillMaxWidth()) {
                            Text("SAVE & MARK SHIPPED")
                        }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(statusLabel(o.status), {}, readOnly = true,
                                label = { Text("Order status") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("new", "confirmed", "shipped", "out_for_delivery", "delivered", "cancelled").forEach { s ->
                                    DropdownMenuItem(text = { Text(statusLabel(s)) }, onClick = {
                                        expanded = false
                                        if (s == "shipped") onShip(o, company, tracking) else onStatus(o, s)
                                    })
                                }
                            }
                        }
                        if (o.status != "delivered") {
                            Button(
                                onClick = { onStatus(o, "delivered") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("MARK AS DELIVERED") }
                        } else {
                            Text("✓ Delivered — customer sees this order as complete.",
                                color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// Put your website address here (the page that opens products), e.g. "https://your-shop.web.app/"
// Leave it empty to skip the order link in the caption.
private const val SHOP_URL = ""

private fun buildCaption(p: Product): String {
    val price = if (p.price % 1.0 == 0.0) p.price.toLong().toString() else "%.2f".format(p.price)
    val sb = StringBuilder()
    sb.append("✨ ${p.name}\n")
    sb.append("💰 Price: ₹$price\n")
    if (p.description.isNotBlank()) sb.append("\n${p.description}\n")
    sb.append("\n🚚 Cash on Delivery — pay when it reaches your door!")
    if (SHOP_URL.isNotBlank()) sb.append("\n🛒 Order here: ${SHOP_URL}?p=${p.id}")
    return sb.toString()
}

// Opens Instagram with the product photos. Instagram does not accept a ready-made caption from other
// apps, so the caption is copied to the clipboard: long-press the caption box in Instagram and tap Paste.
private fun shareToInstagram(context: Context, p: Product) {
    try {
        val dir = java.io.File(context.cacheDir, "share").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val uris = ArrayList<Uri>()
        p.images.forEachIndexed { i, url ->
            if (url.startsWith("data:image")) {
                val file = java.io.File(dir, "product_${i + 1}.jpg")
                val raw = Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT)
                // Only the main photo gets the price badge, and only in the shared copy.
                file.writeBytes(if (i == 0) stampPrice(raw, p) else raw)
                uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(context, "This product has no photos that can be shared.", Toast.LENGTH_LONG).show()
            return
        }
        val caption = buildCaption(p)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Product caption", caption))
        Toast.makeText(context, "Caption copied. In Instagram, long-press the caption box and tap Paste.",
            Toast.LENGTH_LONG).show()

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        intent.apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent(intent).setPackage("com.instagram.android"))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(intent, "Share product"))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

// Draws a price badge in the bottom-right corner of a photo (used only for the copy shared to Instagram).
private fun stampPrice(jpeg: ByteArray, p: Product): ByteArray {
    return try {
        val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return jpeg
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(bmp)
        val priceText = if (p.price % 1.0 == 0.0) p.price.toLong().toString() else "%.2f".format(p.price)
        val label = "₹$priceText"

        val textPx = bmp.width * 0.032f
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = textPx
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val padX = textPx * 0.7f
        val padY = textPx * 0.45f
        val fm = textPaint.fontMetrics
        val w = textPaint.measureText(label) + padX * 2
        val h = (fm.descent - fm.ascent) + padY * 2
        // Right edge, vertically centred.
        val margin = bmp.width * 0.03f
        val top = (bmp.height - h) / 2f
        val rect = android.graphics.RectF(bmp.width - margin - w, top, bmp.width - margin, top + h)
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE617152B.toInt()
        }
        val border = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5C36A.toInt()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = textPx * 0.06f
        }
        canvas.drawRoundRect(rect, h / 2, h / 2, fill)
        canvas.drawRoundRect(rect, h / 2, h / 2, border)
        canvas.drawText(label, rect.left + padX, rect.top + padY - fm.ascent, textPaint)

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.toByteArray()
    } catch (e: Exception) {
        jpeg
    }
}

private fun dataUrlToBitmap(url: String): Bitmap? = try {
    if (!url.startsWith("data:image")) null
    else {
        val bytes = Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
} catch (e: Exception) {
    null
}

private fun decodeAndResize(context: Context, uri: Uri): Bitmap {
    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open selected image")
    val bytes = input.use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sample = Integer.highestOneBit((maxSide / 1000).coerceAtLeast(1))
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: error("Invalid image")
    val scale = minOf(1f, 720f / maxOf(bitmap.width, bitmap.height))
    return if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
}
