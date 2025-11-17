const encoder = new TextEncoder();

interface ImagePayload {
  bytes: Uint8Array;
  width: number;
  height: number;
  name: string;
}

class PdfObjectStore {
  private objects: { id: number; data: Uint8Array }[] = [];

  addRaw(body: string | Uint8Array): number {
    const id = this.objects.length + 1;
    let data: Uint8Array;
    if (typeof body === 'string') {
      const normalized = body.endsWith('\n') ? body : `${body}\n`;
      data = encoder.encode(normalized);
    } else {
      data = body;
    }
    this.objects.push({ id, data });
    return id;
  }

  update(id: number, body: string | Uint8Array): void {
    const data = typeof body === 'string'
      ? encoder.encode(body.endsWith('\n') ? body : `${body}\n`)
      : body;
    this.objects[id - 1] = { id, data };
  }

  addStream(dictionary: string, stream: Uint8Array): number {
    const head = encoder.encode(`${dictionary}\nstream\n`);
    const tail = encoder.encode('\nendstream\n');
    const data = concatUint8([head, stream, tail]);
    return this.addRaw(data);
  }

  build(rootId: number): Uint8Array {
    const header = encoder.encode('%PDF-1.4\n');
    const objectChunks: Uint8Array[] = [];
    const offsets: number[] = [];
    let offset = header.length;
    for (const object of this.objects) {
      const objectHeader = encoder.encode(`${object.id} 0 obj\n`);
      const objectFooter = encoder.encode('\nendobj\n');
      const chunk = concatUint8([objectHeader, object.data, objectFooter]);
      offsets.push(offset);
      offset += chunk.length;
      objectChunks.push(chunk);
    }
    const xrefStart = offset;
    const xrefLines = ['xref', `0 ${this.objects.length + 1}`, '0000000000 65535 f '];
    for (const objectOffset of offsets) {
      xrefLines.push(`${objectOffset.toString().padStart(10, '0')} 00000 n `);
    }
    const xref = encoder.encode(xrefLines.join('\n') + '\n');
    const trailer = encoder.encode(
      `trailer << /Size ${this.objects.length + 1} /Root ${rootId} 0 R >>\nstartxref\n${xrefStart}\n%%EOF`
    );
    return concatUint8([header, ...objectChunks, xref, trailer]);
  }
}

function concatUint8(arrays: Uint8Array[]): Uint8Array {
  const length = arrays.reduce((sum, arr) => sum + arr.length, 0);
  const buffer = new Uint8Array(length);
  let offset = 0;
  for (const arr of arrays) {
    buffer.set(arr, offset);
    offset += arr.length;
  }
  return buffer;
}

function escapeText(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
}

function flattenContent(content: any[]): string[] {
  const lines: string[] = [];
  for (const block of content ?? []) {
    if (!block) continue;
    if (typeof block === 'string') {
      lines.push(block);
    } else if (typeof block.text !== 'undefined') {
      const text = Array.isArray(block.text) ? block.text.join(' ') : block.text;
      if (text) lines.push(String(text));
    } else if (Array.isArray(block.columns)) {
      const columnText = block.columns
        .map((column: any) => {
          if (!column) return '';
          if (typeof column === 'string') return column;
          return column.text ?? '';
        })
        .join('    ');
      if (columnText.trim()) {
        lines.push(columnText);
      }
    } else if (block.table?.body) {
      block.table.body.forEach((row: any[]) => {
        const rowText = row
          .map(cell => {
            if (cell === null || cell === undefined) return '';
            if (typeof cell === 'string') return cell;
            if (typeof cell === 'number') return cell.toString();
            if (cell.text !== undefined) {
              return Array.isArray(cell.text) ? cell.text.join(' ') : cell.text;
            }
            return '';
          })
          .join(' | ');
        lines.push(rowText);
      });
    }
  }
  return lines;
}

function base64ToBytes(base64: string): Uint8Array {
  const binary = atob(base64);
  const len = binary.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function ensureDataUrl(value: string): string {
  if (/^(data:|https?:|blob:)/.test(value)) {
    return value;
  }
  return `data:image/png;base64,${value}`;
}

async function convertImage(name: string, source?: string): Promise<ImagePayload | null> {
  if (!source || typeof document === 'undefined') {
    return null;
  }
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const maxWidth = 520;
      const scale = Math.min(1, maxWidth / (img.naturalWidth || maxWidth));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round((img.naturalWidth || maxWidth) * scale));
      canvas.height = Math.max(1, Math.round((img.naturalHeight || maxWidth / 6) * scale));
      const context = canvas.getContext('2d');
      if (!context) {
        reject(new Error('Canvas context unavailable'));
        return;
      }
      context.drawImage(img, 0, 0, canvas.width, canvas.height);
      const jpeg = canvas.toDataURL('image/jpeg', 0.92);
      const payload = base64ToBytes(jpeg.split(',')[1]);
      resolve({ bytes: payload, width: canvas.width, height: canvas.height, name });
    };
    img.onerror = () => reject(new Error('Failed to load header/footer image'));
    img.src = ensureDataUrl(source);
  });
}

async function buildPdf(docDefinition: any): Promise<Uint8Array> {
  const store = new PdfObjectStore();
  const fontId = store.addRaw('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\n');

  const headerImage = await convertImage('ImH', docDefinition?.header?.image);
  const footerImage = await convertImage('ImF', docDefinition?.footer?.columns?.[0]?.image);

  const xObjectEntries: string[] = [];
  let headerId: number | null = null;
  let footerId: number | null = null;

  if (headerImage) {
    const dict = `<< /Type /XObject /Subtype /Image /Width ${headerImage.width} /Height ${headerImage.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${headerImage.bytes.length} >>`;
    headerId = store.addStream(dict, headerImage.bytes);
    xObjectEntries.push(`/${headerImage.name} ${headerId} 0 R`);
  }

  if (footerImage) {
    const dict = `<< /Type /XObject /Subtype /Image /Width ${footerImage.width} /Height ${footerImage.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${footerImage.bytes.length} >>`;
    footerId = store.addStream(dict, footerImage.bytes);
    xObjectEntries.push(`/${footerImage.name} ${footerId} 0 R`);
  }

  const lines = flattenContent(docDefinition?.content ?? []);
  let cursorY = 700;
  const contentParts: string[] = [];

  if (headerImage && headerId) {
    const x = (595 - headerImage.width) / 2;
    const y = 842 - headerImage.height - 20;
    contentParts.push(`q ${headerImage.width} 0 0 ${headerImage.height} ${x.toFixed(2)} ${y.toFixed(2)} cm /${headerImage.name} Do Q`);
  }

  if (footerImage && footerId) {
    const x = (595 - footerImage.width) / 2;
    const y = 20;
    contentParts.push(`q ${footerImage.width} 0 0 ${footerImage.height} ${x.toFixed(2)} ${y.toFixed(2)} cm /${footerImage.name} Do Q`);
  }

  for (const line of lines) {
    if (!line) continue;
    if (cursorY < 80) {
      break;
    }
    contentParts.push(`BT /F1 12 Tf 1 0 0 1 50 ${cursorY.toFixed(2)} Tm (${escapeText(line)}) Tj ET`);
    cursorY -= 18;
  }

  const contentBytes = encoder.encode(contentParts.join('\n'));
  const contentId = store.addStream(`<< /Length ${contentBytes.length} >>`, contentBytes);

  const resourcesParts = [`/Font << /F1 ${fontId} 0 R >>`];
  if (xObjectEntries.length) {
    resourcesParts.push(`/XObject << ${xObjectEntries.join(' ')} >>`);
  }
  const resources = `<< ${resourcesParts.join(' ')} >>`;
  const pageId = store.addRaw('');
  const pagesId = store.addRaw('');
  const catalogId = store.addRaw('');

  store.update(
    pageId,
    `<< /Type /Page /Parent ${pagesId} 0 R /MediaBox [0 0 595 842] /Resources ${resources} /Contents ${contentId} 0 R >>\n`
  );
  store.update(pagesId, `<< /Type /Pages /Kids [${pageId} 0 R] /Count 1 >>\n`);
  store.update(catalogId, `<< /Type /Catalog /Pages ${pagesId} 0 R >>\n`);

  return store.build(catalogId);
}

class SimplePdfMake {
  createPdf(docDefinition: any) {
    return {
      download: async (filename = 'document.pdf') => {
        try {
          const pdfBytes = await buildPdf(docDefinition);
          if (typeof document === 'undefined') {
            return;
          }
          const buffer = pdfBytes.buffer.slice(0, pdfBytes.byteLength) as ArrayBuffer;
          const blob = new Blob([buffer], { type: 'application/pdf' });
          const link = document.createElement('a');
          link.href = URL.createObjectURL(blob);
          link.download = filename;
          link.click();
          URL.revokeObjectURL(link.href);
        } catch (error) {
          console.error('Unable to generate PDF', error);
        }
      }
    };
  }
}

const pdfMake = new SimplePdfMake();

export default pdfMake;
