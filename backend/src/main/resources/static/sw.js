// Lookups need the network, so requests pass straight through. The fetch handler exists because
// browsers only offer to install a web app that has one.
self.addEventListener("fetch", event => event.respondWith(fetch(event.request)));
