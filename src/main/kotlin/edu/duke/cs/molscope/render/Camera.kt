package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import org.joml.AABBf
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import java.lang.Float.min
import java.nio.ByteBuffer


class Camera internal constructor(
	internal val device: Device
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = also { closer.add(this@autoClose) }
	override fun close() = closer.close()

	val pos: Vector3f = Vector3f(0f, 0f, -2f)
	val lookAt: Vector3f = Vector3f(0f, 0f, 0f)
	val up: Vector3f = Vector3f(0f, 1f, 0f)
	val size: Vector2f = Vector2f(320f, 160f)
	var zNear: Float = 1f
	var zFar: Float = 3f
	var magnification: Float = 40f

	val side: Vector3f = Vector3f()
	val look: Vector3f = Vector3f()

	/** side = up x look */
	fun updateSide() = side.set(up).cross(look)

	/** look = norm(lookAt - pos) */
	fun updateLook() = look.set(lookAt).sub(pos).normalize()

	init {
		updateSide()
		updateLook()
	}

	val buf = device
		.buffer(
			size = 21*Float.SIZE_BYTES.toLong(),
			usage = IntFlags.of(Buffer.Usage.UniformBuffer, Buffer.Usage.TransferDst)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()

	fun upload() {
		buf.transferHtoD { buf ->

			fun ByteBuffer.put(v: Vector3f) {
				putFloat(v.x)
				putFloat(v.y)
				putFloat(v.z)
				putFloat(Float.NaN) // 4 bytes pad
			}

			fun ByteBuffer.put(v: Vector2f) {
				putFloat(v.x)
				putFloat(v.y)
			}

			buf.put(pos)
			buf.put(side)
			buf.put(up)
			buf.put(look)
			buf.put(size)
			buf.putFloat(zNear)
			buf.putFloat(zFar)
			buf.putFloat(magnification)
			buf.flip()
		}
	}

	fun lookAtBox(width: Int, height: Int, focalLength: Float, look: Vector3f, up: Vector3f, box: AABBf) {

		// set camera orientation
		this.look.set(look).normalize()
		this.up.set(up).normalize()
		updateSide()

		// copy window size
		size.set(width.toFloat(), height.toFloat())

		// look at the center of the box
		lookAt.boxCenter(box)

		// get the nearest and farthest corners of the box
		val cornersDist = (0 until box.numCorners)
			.map { i -> Vector3f().boxCorner(box, i) }
			.map { p -> p to Vector3f(p).sub(lookAt).parallelTo(this.look).dot(this.look) }
		val (nearCorner, near) = cornersDist.minBy { (_, dot) -> dot }!!
		val (farCorner, far) = cornersDist.maxBy { (_, dot) -> dot }!!

		pos.set(this.look)
			.mul(near - focalLength)
			.add(lookAt)
		zNear = focalLength
		zFar = focalLength + far - near

		// calc the smallest magnification that shows the whole box
		val center = Vector3f().boxCenter(box)
		magnification = min(
			min(
				width/(box.maxX - center.x)/2,
				width/(center.x - box.minX)/2
			),
			min(
				height/(box.maxY - center.y)/2,
				height/(center.y - box.minY)/2
			)
		)
	}

	fun rotate(q: Quaternionf) {

		// rotate the position about the look point
		pos
			.sub(lookAt)
			.rotate(q)
			.add(lookAt)

		// rotate the orientation too
		side.rotate(q)
		up.rotate(q)
		look.rotate(q)
	}
}

fun Vector3f.worldToCamera(camera: Camera) = this
	.sub(camera.pos)
	.set(
		camera.side.dot(this),
		camera.up.dot(this),
		camera.look.dot(this)
	)
